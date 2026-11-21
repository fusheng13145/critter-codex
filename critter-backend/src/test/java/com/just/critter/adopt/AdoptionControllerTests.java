package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionApplicationService;
import com.just.critter.adopt.service.CreatedAdoption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdoptionApplicationService adoptionApplicationService;

    @Test
    @DisplayName("登录用户提交申请：从鉴权上下文取用户并返回待审核单")
    void createUsesAuthenticatedUserAndReturnsPendingAdoption() throws Exception {
        when(adoptionApplicationService.create(any(), eq(1001L)))
                .thenReturn(new CreatedAdoption(
                        "AD20261121003", AdoptionStatus.PENDING_AUDIT, 3000, AdoptionPeriod.MONTH));

        mockMvc.perform(post("/api/v1/adoptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalCode": "CAT-2026-031",
                                  "planId": 2,
                                  "promiseAccepted": true,
                                  "contact": "13800000000",
                                  "remark": "希望定期看照片"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.adoptionNo").value("AD20261121003"))
                .andExpect(jsonPath("$.data.status").value("PENDING_AUDIT"))
                .andExpect(jsonPath("$.data.amount").value(3000))
                .andExpect(jsonPath("$.data.period").value("MONTH"));

        verify(adoptionApplicationService).create(any(), eq(1001L));
    }
}
