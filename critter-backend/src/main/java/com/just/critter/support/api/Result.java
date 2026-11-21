package com.just.critter.support.api;

import com.just.critter.support.exception.ErrorCode;

import java.time.OffsetDateTime;

/**
 * 统一响应体。业务失败默认 HTTP 200，鉴权/权限/不存在/并发/系统异常按码映射 401/403/404/409/500；
 * 具体结果一律看 code，requestId 由过滤器回填。
 */
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;
    private String requestId;
    private final OffsetDateTime timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = OffsetDateTime.now();
    }

    public static Result<Void> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(ErrorCode errorCode, Object data) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), (T) data);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(int code, String message, Object data) {
        return new Result<>(code, message, (T) data);
    }

    public Result<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
}
