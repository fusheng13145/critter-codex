package com.just.critter.support.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验收标准（接口设计说明书 §2、§3、§7.4）：同一响应壳、按码映射 HTTP、
 * X-Request-Id 回传、非法迁移带 data.allowed 且 message 为中文。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("成功响应：code=0，回传 X-Request-Id 且响应体一致")
    void success_envelope_carries_request_id() throws Exception {
        mvc.perform(get("/__probe/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("上游带 X-Request-Id 时响应头与响应体原样透传")
    void incoming_request_id_is_propagated() throws Exception {
        mvc.perform(get("/__probe/ok").header(RequestIdFilter.HEADER, "trace-42"))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-42"))
                .andExpect(jsonPath("$.requestId").value("trace-42"));
    }

    @Test
    @DisplayName("无权限 403、记录不存在 404、并发冲突 409、系统异常 500")
    void http_status_follows_error_code() throws Exception {
        mvc.perform(get("/__probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20002));
        mvc.perform(get("/__probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
        mvc.perform(get("/__probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(50002));
        mvc.perform(get("/__probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("非法迁移：HTTP 200 + 中文 message + data{current,target,allowed}")
    void illegal_transition_contract() throws Exception {
        mvc.perform(get("/__probe/transition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30402))
                .andExpect(jsonPath("$.message").value(containsString("在校园活动")))
                .andExpect(jsonPath("$.message").value(containsString("送医并登记就诊记录")))
                .andExpect(jsonPath("$.data.current").value("ON_CAMPUS"))
                .andExpect(jsonPath("$.data.target").value(nullValue()))
                .andExpect(jsonPath("$.data.allowed[0]").value("TREATING"))
                .andExpect(jsonPath("$.data.allowed[3]").value("DECEASED"));
    }
}
