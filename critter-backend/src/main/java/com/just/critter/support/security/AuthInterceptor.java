package com.just.critter.support.security;

import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 接口层鉴权：解析 Bearer 令牌、查黑名单、写入当前用户、按 {@link RequireRoles} 拦角色。
 * 服务层还会再做一次"角色-动作"白名单校验（接口设计 §4 的两层判定），
 * 这里是第一层，注解配错时服务层兜底。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_PREFIX = "auth:black:";
    private static final String MIN_IAT_PREFIX = "auth:min-iat:";

    private final JwtService jwtService;
    private final KeyValueStore store;

    public AuthInterceptor(JwtService jwtService, KeyValueStore store) {
        this.jwtService = jwtService;
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 文档明确公开的只读接口：匿名可看，写操作仍要求令牌。
        if (isPublicGet(request)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        JwtService.Payload payload = jwtService.parse(token);
        if (!payload.isAccess()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (store.exists(BLACKLIST_PREFIX + payload.jwtId())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // 改密后旧令牌立即失效：只比签发时间，不需要把每个 jti 都拉黑。
        // 用 <= 而不是 <：iat 只有秒级精度，同一秒内签发的旧令牌也必须失效。
        String minIssuedAt = store.get(MIN_IAT_PREFIX + payload.userId()).orElse(null);
        if (minIssuedAt != null && payload.issuedAt().getEpochSecond() <= Long.parseLong(minIssuedAt)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        AuthContext.set(new CurrentUser(
                payload.userId(), payload.userNo(), null, payload.primaryRole(),
                payload.roles(), payload.dept(), payload.jwtId(), payload.expiresAt()));

        checkRoles(handler);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    /**
     * 给"匿名可访问但登录后行为不同"的接口用（如附件内容：公开照片匿名可看，票据必须登录）。
     * 有合法令牌就写入上下文，没有或非法则返回 null，不抛异常。
     */
    public CurrentUser authenticateIfPresent(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            JwtService.Payload payload = jwtService.parse(authorization.substring(BEARER_PREFIX.length()).trim());
            if (!payload.isAccess() || store.exists(BLACKLIST_PREFIX + payload.jwtId())) {
                return null;
            }
            String minIssuedAt = store.get(MIN_IAT_PREFIX + payload.userId()).orElse(null);
            if (minIssuedAt != null && payload.issuedAt().getEpochSecond() <= Long.parseLong(minIssuedAt)) {
                return null;
            }
            CurrentUser user = new CurrentUser(payload.userId(), payload.userNo(), null, payload.primaryRole(),
                    payload.roles(), payload.dept(), payload.jwtId(), payload.expiresAt());
            AuthContext.set(user);
            return user;
        } catch (BusinessException ex) {
            return null;
        }
    }

    /**
     * 公开只读路径：个体列表/详情、群体列表/详情、物种、点位、出现记录。
     * 用正则限制为"单段编号"，避免把 /health、/history 等登录接口一起放行。
     */
    private boolean isPublicGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.equals("/api/v1/animals")
                || path.equals("/api/v1/groups")
                || path.equals("/api/v1/species")
                || path.equals("/api/v1/points")
                || path.equals("/api/v1/occurrences")
                || path.matches("^/api/v1/animals/[^/]+$")
                || path.matches("^/api/v1/groups/[^/]+$");
    }

    private void checkRoles(Object handler) {        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        RequireRoles required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireRoles.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireRoles.class);
        }
        if (required == null || required.value().length == 0) {
            return;
        }
        CurrentUser user = AuthContext.require();
        boolean allowed = Arrays.stream(required.value()).anyMatch(user::hasRole);
        if (!allowed) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
    }
}
