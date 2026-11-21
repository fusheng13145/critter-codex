package com.just.critter.support.exception;

/**
 * 业务异常：必须带码，由全局处理器转成 code/message/data。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.data = data;
    }

    public BusinessException(int code, String message) {
        this(code, message, null);
    }

    public BusinessException(int code, String message, Object data) {
        super(resolveMessage(code, message));
        this.code = code;
        this.data = data;
    }

    private static String resolveMessage(int code, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return ErrorCode.fromCode(code)
                .map(ErrorCode::getMessage)
                .orElse("业务处理失败");
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
