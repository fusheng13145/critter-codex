package com.just.critter.support.exception;

import org.springframework.http.HttpStatus;

/**
 * 错误码到 HTTP 状态映射（接口设计说明书 §3 HTTP 列）：
 * 业务失败默认 200；鉴权 401、权限 403、不存在 404、并发 409、系统异常 500。
 */
public final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    public static HttpStatus of(int code) {
        return switch (code) {
            case 20001, 30102 -> HttpStatus.UNAUTHORIZED;
            case 20002, 20003, 20004 -> HttpStatus.FORBIDDEN;
            case 30410 -> HttpStatus.NOT_FOUND;
            case 50002 -> HttpStatus.CONFLICT;
            case 50001, 50003 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
    }
}
