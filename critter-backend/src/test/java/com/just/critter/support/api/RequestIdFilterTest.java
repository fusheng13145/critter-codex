package com.just.critter.support.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（接口设计说明书 §1 追踪）：响应头回传 X-Request-Id，
 * 上游已带则原样透传，未带则生成；同一值写入请求属性供响应体回填。
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("上游带了 X-Request-Id 就原样透传")
    void keeps_incoming_request_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-001");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo("trace-001");
    }

    @Test
    @DisplayName("未带时生成一个非空 requestId 并回传")
    void generates_request_id_when_absent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotBlank();
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo(header);
    }
}
