package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.adopt.service.AuditedAdoption;
import com.just.critter.support.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionAuditControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdoptionAuditService adoptionAuditService;

    @Test
    @DisplayName("管理员审核通过：从鉴权上下文取审核人并返回待缴费期限")
    void approvesUsingAuthenticatedAuditor() throws Exception {
        when(adoptionAuditService.audit(eq("AD20261121003"), any(), eq(1001L)))
                .thenReturn(new AuditedAdoption(
                        "AD20261121003",
                        AdoptionStatus.PENDING_PAYMENT,
                        OffsetDateTime.parse("2026-11-28T12:00:00+08:00")));

        mockMvc.perform(put("/api/v1/adoptions/AD20261121003/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "reason": "学籍与数量核对通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.adoptionNo").value("AD20261121003"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.payDeadline").value("2026-11-28T12:00:00+08:00"));

        verify(adoptionAuditService).audit(eq("AD20261121003"), any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人调用审核接口返回 20003")
    void rejectsNonAdminAuditor() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(put("/api/v1/adoptions/AD20261121003/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","reason":"学籍与数量核对通过"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }
}
