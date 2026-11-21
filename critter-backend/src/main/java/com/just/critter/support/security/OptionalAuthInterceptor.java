package com.just.critter.support.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 可选鉴权拦截器：用于 03 文档标注为"公开"的接口族
 * （/animals、/groups、/species、/occurrences、/points、/notices、/projects、/plans、/comments）。
 *
 * <p>行为：带了合法 Bearer 令牌就解析并写入 {@link AuthContext}，服务层据此做字段裁剪与审计；
 * 没带令牌、令牌过期或非法时一律放行，不抛 20001。这样公开页面（如档案公开页、
 * 物种名录、公告）无需登录即可浏览，而登录用户仍能拿到自己的身份。</p>
 *
 * <p>与 {@link AuthInterceptor} 分开成两个 Bean 的意义：拦截器注册表按路径区分，
 * 不靠"白名单"把接口整段变成匿名——匿名白名单里的请求永远不会解析令牌。</p>
 */
public class OptionalAuthInterceptor implements HandlerInterceptor {

    private final AuthInterceptor delegate;

    public OptionalAuthInterceptor(AuthInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        delegate.authenticateIfPresent(request);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程复用，必须清掉，否则下一个请求会串到上一个用户
        AuthContext.clear();
    }
}
