package com.just.critter.support.exception;

import com.just.critter.support.api.RequestIdFilter;
import com.just.critter.support.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/**
 * 全局异常处理器：三层异常统一收敛为 code/message/data，并按错误码映射 HTTP 状态；
 * 响应体回填 requestId，日志与响应分离，不把堆栈返回前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /** 上传超限：Spring 在进入 Controller 之前就抛，必须单独收敛，否则会落成 50001。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                          HttpServletRequest request) {
        return respond(ErrorCode.FILE_INVALID.getCode(),
                Result.fail(ErrorCode.FILE_INVALID.getCode(), "文件大小超出限制（单个文件不超过 5MB）", null)
                        .withRequestId(RequestIdFilter.current(request)));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Result<?>> handleMissingPart(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.PARAM_INVALID.getCode(),
                Result.fail(ErrorCode.PARAM_INVALID.getCode(), "缺少必要的参数：" + ex.getMessage(), null)
                        .withRequestId(RequestIdFilter.current(request)));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<?>> handleFormat(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.FORMAT_INVALID.getCode(),
                Result.fail(ErrorCode.FORMAT_INVALID).withRequestId(RequestIdFilter.current(request)));
    }

    /** 唯一键冲突（如字典 type+key 重复、并发注册同一学号）：按 30411 编号重复提示。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<?>> handleDuplicate(DuplicateKeyException ex, HttpServletRequest request) {
        return respond(ErrorCode.CODE_DUPLICATED.getCode(),
                Result.fail(ErrorCode.CODE_DUPLICATED).withRequestId(RequestIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUncaught(Exception ex, HttpServletRequest request) {
        log.error("未捕获异常 requestId={}", RequestIdFilter.current(request), ex);
        return respond(ErrorCode.SYSTEM_ERROR.getCode(), Result.fail(ErrorCode.SYSTEM_ERROR)
                .withRequestId(RequestIdFilter.current(request)));
    }

    private static ResponseEntity<Result<?>> respond(int code, Result<?> body) {
        return ResponseEntity.status(ErrorHttpStatus.of(code)).body(body);
    }
}
