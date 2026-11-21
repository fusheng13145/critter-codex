package com.just.critter.support.security;

import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;

/**
 * 当前请求的用户上下文。拦截器写入、请求结束清理；
 * 业务层不直接读 SecurityContext（本项目未引入完整 Spring Security）。
 */
public final class AuthContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 必须已登录，否则 20001（HTTP 401）。 */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
