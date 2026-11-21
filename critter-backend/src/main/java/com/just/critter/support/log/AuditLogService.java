package com.just.critter.support.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.domain.model.SysLog;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.support.api.RequestIdFilter;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志统一入口（FR-SYS-02）。约定：
 * 登录、注册、改密、资料变更、身份申请、字典增改都必须写；
 * 写日志失败不影响主流程（catch 后仅告警），但不能静默丢失整类动作。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final int MAX_ACTION = 48;
    private static final int MAX_BIZ_ID = 32;
    private static final int MAX_IP = 45;
    private static final int MAX_REQUEST_ID = 40;

    private final SysLogMapper sysLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(SysLogMapper sysLogMapper, ObjectMapper objectMapper) {
        this.sysLogMapper = sysLogMapper;
        this.objectMapper = objectMapper;
    }

    public void success(String action, String bizType, String bizId, Object before, Object after) {
        record(null, null, action, bizType, bizId, before, after, "SUCCESS", null);
    }

    public void fail(String action, String bizType, String bizId, int failCode, Object detail) {
        record(null, null, action, bizType, bizId, detail, null, "FAIL", failCode);
    }

    /** 登录这类匿名入口拿不到 AuthContext，需要显式指定操作人。 */
    public void successAs(long actorId, String actorRole, String action, String bizType, String bizId,
                          Object before, Object after) {
        record(actorId, actorRole, action, bizType, bizId, before, after, "SUCCESS", null);
    }

    public void failAs(long actorId, String actorRole, String action, String bizType, String bizId,
                       int failCode, Object detail) {
        record(actorId, actorRole, action, bizType, bizId, detail, null, "FAIL", failCode);
    }

    private void record(Long actorIdOverride, String actorRoleOverride, String action, String bizType,
                        String bizId, Object before, Object after, String result, Integer failCode) {
        try {
            SysLog entity = new SysLog();
            CurrentUser current = AuthContext.get();
            long actorId = actorIdOverride != null ? actorIdOverride : (current == null ? 0L : current.id());
            entity.setActorId(actorId);
            entity.setActorRole(actorRoleOverride != null ? actorRoleOverride
                    : (current == null ? "GUEST" : current.primaryRole()));
            entity.setAction(truncate(action, MAX_ACTION));
            entity.setBizType(bizType);
            entity.setBizId(bizId == null ? null : truncate(bizId, MAX_BIZ_ID));
            entity.setBeforeJson(toJson(before));
            entity.setAfterJson(toJson(after));
            entity.setResult(result);
            entity.setFailCode(failCode);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setIp(clientIp(request));
                entity.setRequestId(truncate(RequestIdFilter.current(request), MAX_REQUEST_ID));
            }
            sysLogMapper.insert(entity);
        } catch (RuntimeException ex) {
            log.warn("写操作日志失败 action={} biz={}/{}", action, bizType, bizId, ex);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
