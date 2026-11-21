package com.just.critter.adopt;

import java.util.List;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionPlanControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdoptionPlanService adoptionPlanService;

    @Test
    @DisplayName("公开列表：返回 v1.1 统一响应壳与启用方案")
    void listReturnsEnabledPlansInUnifiedResponse() throws Exception {
        AdoptionPlan plan = new AdoptionPlan(
                1L, "测试月度方案", AdoptionPeriod.MONTH, 3000, "测试权益", true);
        when(adoptionPlanService.listEnabledPlans()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].planId").value(1))
                .andExpect(jsonPath("$.data[0].planName").value("测试月度方案"))
                .andExpect(jsonPath("$.data[0].period").value("MONTH"))
                .andExpect(jsonPath("$.data[0].amount").value(3000))
                .andExpect(jsonPath("$.data[0].benefits").value("测试权益"));
    }

    @Test
    @DisplayName("公开列表：即使携带无效令牌也不能被鉴权拦截")
    void listIsAccessibleWithoutValidBearerToken() throws Exception {
        when(adoptionPlanService.listEnabledPlans()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
