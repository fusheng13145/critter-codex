package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionDetail;
import com.just.critter.adopt.service.AdoptionLifecycleService;
import com.just.critter.adopt.service.AdoptionPaymentRecord;
import com.just.critter.adopt.service.AdoptionPaymentResult;
import com.just.critter.adopt.service.AdoptionPaymentService;
import com.just.critter.adopt.service.AdoptionQueryService;
import com.just.critter.adopt.service.AdoptionSummary;
import com.just.critter.adopt.service.CertificateView;
import com.just.critter.adopt.service.TerminatedAdoption;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AdoptionLifecycleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdoptionQueryService queryService;

    @MockitoBean
    private AdoptionPaymentService paymentService;

    @MockitoBean
    private AdoptionLifecycleService lifecycleService;

    @Test
    @DisplayName("管理员认养单列表：按状态与分页参数返回统一分页结构")
    void listsAdoptionsForAdmin() throws Exception {
        when(queryService.list(eq("PENDING_AUDIT"), any()))
                .thenReturn(PageResult.of(List.of(summary()), 2, 10, 21));

        mockMvc.perform(get("/api/v1/adoptions")
                        .param("status", "PENDING_AUDIT")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].adoptionNo").value("AD20261121003"))
                .andExpect(jsonPath("$.data.list[0].contactMasked").value("138****0000"))
                .andExpect(jsonPath("$.data.page.page").value(2))
                .andExpect(jsonPath("$.data.page.total").value(21));

        verify(queryService).list(eq("PENDING_AUDIT"), any());
    }

    @Test
    @DisplayName("我的认养：从鉴权上下文取当前用户")
    void listsMyAdoptions() throws Exception {
        when(queryService.mine(eq(1001L), eq("ACTIVE"), any()))
                .thenReturn(PageResult.of(List.of(summary()), 1, 20, 1));

        mockMvc.perform(get("/api/v1/adoptions/mine").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].adoptionNo").value("AD20261121003"));

        verify(queryService).mine(eq(1001L), eq("ACTIVE"), any());
    }

    @Test
    @DisplayName("认养详情：本人可读并返回缴费流水")
    void readsAdoptionDetail() throws Exception {
        when(queryService.detail(eq("AD20261121003"), eq(1001L), eq(true)))
                .thenReturn(detail());

        mockMvc.perform(get("/api/v1/adoptions/AD20261121003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adoptionNo").value("AD20261121003"))
                .andExpect(jsonPath("$.data.payments[0].paymentNo").value("PAY20261121001"));

        verify(queryService).detail(eq("AD20261121003"), eq(1001L), eq(true));
    }

    @Test
    @DisplayName("管理员缴费入账：返回生效日期与证书号")
    void confirmsPayment() throws Exception {
        when(paymentService.pay(eq("AD20261121003"), any(), eq(1001L)))
                .thenReturn(new AdoptionPaymentResult("AD20261121003", AdoptionStatus.ACTIVE,
                        LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 21),
                        "QYWL-AD-2026-0031", "ADOPTED"));

        mockMvc.perform(put("/api/v1/adoptions/AD20261121003/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paidAmount": 3000,
                                  "paidAt": "2026-11-21T10:00:00+08:00",
                                  "voucherFileIds": ["F20261121001"],
                                  "note": "线下转账"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.certificateNo").value("QYWL-AD-2026-0031"))
                .andExpect(jsonPath("$.data.animalStatus").value("ADOPTED"));

        verify(paymentService).pay(eq("AD20261121003"), any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能登记缴费，返回 20003")
    void rejectsStudentPaymentConfirmation() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(put("/api/v1/adoptions/AD20261121003/payment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidAmount":3000,"paidAt":"2026-11-21T10:00:00+08:00",
                                 "voucherFileIds":["F20261121001"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("管理员终止认养：原因必填并返回结束日期")
    void terminatesAdoption() throws Exception {
        when(lifecycleService.terminate(eq("AD20261121003"), eq("认养人申请终止")))
                .thenReturn(new TerminatedAdoption("AD20261121003", AdoptionStatus.TERMINATED,
                        LocalDate.of(2026, 11, 21), "ON_CAMPUS"));

        mockMvc.perform(put("/api/v1/adoptions/AD20261121003/terminate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"认养人申请终止"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TERMINATED"))
                .andExpect(jsonPath("$.data.endDate").value("2026-11-21"));

        verify(lifecycleService).terminate(eq("AD20261121003"), eq("认养人申请终止"));
    }

    @Test
    @DisplayName("电子证书：返回可打印证书数据")
    void readsCertificate() throws Exception {
        when(queryService.certificate(eq("AD20261121003"), eq(1001L), eq(true)))
                .thenReturn(new CertificateView("AD20261121003", "QYWL-AD-2026-0031",
                        "青檐一号", "CAT-2026-031", "三花", "月度守护",
                        LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 21)));

        mockMvc.perform(get("/api/v1/adoptions/AD20261121003/certificate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNo").value("QYWL-AD-2026-0031"))
                .andExpect(jsonPath("$.data.adopterName").value("青檐一号"));
    }

    private AdoptionSummary summary() {
        return new AdoptionSummary("AD20261121003", 1001L, "青檐一号", "CAT-2026-031",
                "三花", "月度守护", 3000, AdoptionPeriod.MONTH, AdoptionStatus.PENDING_AUDIT,
                "138****0000", null, null, null,
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }

    private AdoptionDetail detail() {
        return new AdoptionDetail("AD20261121003", 1001L, "青檐一号", "CAT-2026-031",
                "三花", "月度守护", 3000, AdoptionPeriod.MONTH, AdoptionStatus.ACTIVE,
                "13800000000", "材料齐全", LocalDate.of(2026, 11, 21),
                LocalDate.of(2026, 12, 21), "QYWL-AD-2026-0031", null,
                LocalDateTime.of(2026, 11, 21, 10, 0),
                List.of(new AdoptionPaymentRecord("PAY20261121001", 3000,
                        LocalDateTime.of(2026, 11, 21, 10, 0), List.of("F20261121001"),
                        "线下转账", LocalDateTime.of(2026, 11, 21, 10, 5))));
    }
}
