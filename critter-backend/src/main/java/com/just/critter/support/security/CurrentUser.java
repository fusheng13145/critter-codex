package com.just.critter.support.security;

import java.time.Instant;
import java.util.Set;

/**
 * 请求上下文里的当前用户。只放鉴权必需信息，联系方式不进令牌（接口设计 §4）。
 */
public record CurrentUser(
        long id,
        String userNo,
        String nickname,
        String primaryRole,
        Set<String> roles,
        String dept,
        String jwtId,
        Instant tokenExpiresAt) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /** 系统管理员与动物管理员都算"管理员"口径。 */
    public boolean isAdmin() {
        return hasRole("ANIMAL_ADMIN") || hasRole("SYSTEM_ADMIN");
    }

    public boolean isSystemAdmin() {
        return hasRole("SYS_ADMIN") || hasRole("SYSTEM_ADMIN");
    }
}
