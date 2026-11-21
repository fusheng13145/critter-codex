package com.just.critter.support.exception;

import com.just.critter.support.api.RequestIdFilter;
import com.just.critter.support.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理器：三层异常统一收敛为 code/message/data，并按错误码映射 HTTP 状态；
 * 响应体回填 requestId，日志与响应分离，不把堆栈返回前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return respond(ex.getCode(), Result.fail(ex.getCode(), ex.getMessage(), ex.getData())
                .withRequestId(RequestIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidation(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.PARAM_INVALID.getCode(), Result.fail(ErrorCode.PARAM_INVALID, errors)
                .withRequestId(RequestIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUncaught(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.SYSTEM_ERROR.getCode(), Result.fail(ErrorCode.SYSTEM_ERROR)
                .withRequestId(RequestIdFilter.current(request)));
    }

    private static ResponseEntity<Result<?>> respond(int code, Result<?> body) {
        return ResponseEntity.status(ErrorHttpStatus.of(code)).body(body);
    }
}
