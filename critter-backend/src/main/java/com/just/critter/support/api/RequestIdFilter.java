package com.just.critter.support.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪：响应头回传 X-Request-Id（接口设计说明书 §1），
 * 上游已带则透传，未带则生成；同时写入请求属性供响应体回填。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "critter.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        filterChain.doFilter(request, response);
    }

    /** 供异常处理器回填响应体；过滤器未生效时返回 null。 */
    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
