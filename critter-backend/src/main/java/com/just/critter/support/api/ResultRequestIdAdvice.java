package com.just.critter.support.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 给所有统一响应体回填 requestId（接口设计说明书 §1 追踪），避免每个 Controller 手写。
 */
@ControllerAdvice
public class ResultRequestIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class selectedConverterType, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Result<?> result && result.getRequestId() == null) {
            String requestId = currentRequestId();
            if (requestId != null) {
                result.withRequestId(requestId);
            }
        }
        return body;
    }

    private String currentRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return RequestIdFilter.current(servlet.getRequest());
        }
        return null;
    }
}
