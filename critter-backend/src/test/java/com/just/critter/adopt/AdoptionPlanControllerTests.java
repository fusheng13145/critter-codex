package com.just.critter.adopt;

import java.util.List;
import java.util.Set;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanCommand;
import com.just.critter.adopt.service.AdoptionPlanService;
import com.just.critter.support.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionPlanControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

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

    @Test
    @DisplayName("新增方案：管理员提交后返回新方案")
    void createsPlanAsAdmin() throws Exception {
        AdoptionPlan plan = new AdoptionPlan(
                9L, "校园月度守护", AdoptionPeriod.MONTH, 3000, "每月一次照片", true);
        when(adoptionPlanService.create(any(AdoptionPlanCommand.class), eq(1001L))).thenReturn(plan);

        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planName":"校园月度守护","period":"MONTH","amount":3000,
                                 "benefits":"每月一次照片","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planId").value(9))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("新增方案：普通用户返回 403")
    void rejectsNonAdminCreate() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planName":"方案","period":"MONTH","amount":3000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("编辑方案：管理员可停用方案")
    void updatesPlanAsAdmin() throws Exception {
        AdoptionPlan plan = new AdoptionPlan(
                2L, "季度守护", AdoptionPeriod.QUARTER, 9000, null, false);
        when(adoptionPlanService.update(eq(2L), any(AdoptionPlanCommand.class), eq(1001L)))
                .thenReturn(plan);

        mockMvc.perform(put("/api/v1/plans/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planName":"季度守护","period":"QUARTER","amount":9000,"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planId").value(2))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(adoptionPlanService).update(eq(2L), any(AdoptionPlanCommand.class), eq(1001L));
    }

    @Test
    @DisplayName("管理员列表：普通用户带 includeDisabled 返回 403")
    void rejectsNonAdminDisabledList() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(get("/api/v1/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("includeDisabled", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }
}
