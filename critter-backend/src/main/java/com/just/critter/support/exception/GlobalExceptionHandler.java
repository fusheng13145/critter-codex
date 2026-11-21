package com.just.critter.support.exception;

import com.just.critter.support.api.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 全局异常处理器：三层异常统一收敛为 code/message/data，日志与响应分离，不把堆栈返回前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage(), ex.getData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return Result.fail(ErrorCode.PARAM_INVALID, errors);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleUncaught(Exception ex) {
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
