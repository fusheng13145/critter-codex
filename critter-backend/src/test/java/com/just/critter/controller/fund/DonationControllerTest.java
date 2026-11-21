package com.just.critter.controller.fund;

import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.service.fund.BatchConfirmResult;
import com.just.critter.service.fund.BatchFailure;
import com.just.critter.service.fund.ConfirmDonationCommand;
import com.just.critter.service.fund.CreateDonationCommand;
import com.just.critter.service.fund.CreatedDonation;
import com.just.critter.service.fund.DonationConfirmation;
import com.just.critter.service.fund.DonationFilter;
import com.just.critter.service.fund.DonationService;
import com.just.critter.service.fund.DonationSummary;
import com.just.critter.service.fund.ProjectProgress;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.AuthInterceptor;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T3.1 接口契约：捐赠登记、管理员列表、单笔核对、批量核对。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DonationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private DonationService donationService;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(student());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("POST /donations 登录学生登记，返回 PENDING_VERIFY 与 note")
    void create_returns_pending_note() throws Exception {
        when(donationService.create(any(), any())).thenReturn(new CreatedDonation(
                "DN202611210031", DonationStatus.PENDING_VERIFY,
                "登记已收到，待管理员核对到账后计入项目进度"));

        mvc.perform(post("/api/v1/donations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectNo": "PRJ-2026-007",
                                  "amount": 20000,
                                  "anonymous": true,
                                  "message": "给小猫加餐",
                                  "voucherFileIds": ["file-1", "file-2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.donationNo").value("DN202611210031"))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFY"))
                .andExpect(jsonPath("$.data.note").value("登记已收到，待管理员核对到账后计入项目进度"));

        ArgumentCaptor<CreateDonationCommand> captor = ArgumentCaptor.forClass(CreateDonationCommand.class);
        verify(donationService).create(captor.capture(), any());
        assertThat(captor.getValue().projectNo()).isEqualTo("PRJ-2026-007");
        assertThat(captor.getValue().amount()).isEqualTo(20_000);
        assertThat(captor.getValue().anonymous()).isTrue();
        assertThat(captor.getValue().message()).isEqualTo("给小猫加餐");
        assertThat(captor.getValue().voucherFileIds()).containsExactly("file-1", "file-2");
    }

    @Test
    @DisplayName("GET /donations 解析状态与项目筛选，返回待核对/已入账列表")
    void list_parses_filters() throws Exception {
        when(donationService.search(any(), any())).thenReturn(PageResult.of(
                List.of(new DonationSummary("DN202611210031", "PRJ-2026-007", "冬季流浪猫医疗基金",
                        9001L, "小林", 20_000, false, "给小猫加餐", List.of("file-1"),
                        DonationStatus.PENDING_VERIFY, null, null,
                        OffsetDateTime.parse("2026-11-21T10:31:00+08:00"),
                        OffsetDateTime.parse("2026-11-21T10:31:00+08:00"))),
                1, 20, 1));

        mvc.perform(get("/api/v1/donations")
                        .param("status", "PENDING_VERIFY,ABNORMAL")
                        .param("projectNo", "PRJ-2026-007")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].donationNo").value("DN202611210031"))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING_VERIFY"))
                .andExpect(jsonPath("$.data.page.total").value(1));

        ArgumentCaptor<DonationFilter> filterCaptor = ArgumentCaptor.forClass(DonationFilter.class);
        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);
        verify(donationService).search(filterCaptor.capture(), pageCaptor.capture());
        assertThat(filterCaptor.getValue().statuses())
                .containsExactlyInAnyOrder(DonationStatus.PENDING_VERIFY, DonationStatus.ABNORMAL);
        assertThat(filterCaptor.getValue().projectNo()).isEqualTo("PRJ-2026-007");
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("PUT /donations/{no}/confirm 解析实收金额/时间/凭证，返回进度")
    void confirm_parses_command_and_returns_progress() throws Exception {
        AuthContext.set(admin());
        when(donationService.confirm(eq("DN202611210031"), any(), any())).thenReturn(new DonationConfirmation(
                "DN202611210031", DonationStatus.VERIFIED, 20_000,
                OffsetDateTime.parse("2026-11-21T10:31:00+08:00"),
                new ProjectProgress("PRJ-2026-007", 60_000, 300_000, 20), "到账"));

        mvc.perform(put("/api/v1/donations/DN202611210031/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 20000,
                                  "paidAt": "2026-11-20T10:00:00+08:00",
                                  "voucherFileIds": ["file-proof"],
                                  "note": "到账"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedAt").value("2026-11-21T10:31:00+08:00"))
                .andExpect(jsonPath("$.data.progress.raisedAmount").value(60_000))
                .andExpect(jsonPath("$.data.progress.progressPercent").value(20));

        ArgumentCaptor<ConfirmDonationCommand> captor = ArgumentCaptor.forClass(ConfirmDonationCommand.class);
        verify(donationService).confirm(eq("DN202611210031"), captor.capture(), any());
        assertThat(captor.getValue().amount()).isEqualTo(20_000);
        assertThat(captor.getValue().paidAt()).isEqualTo(LocalDateTime.parse("2026-11-20T10:00:00"));
        assertThat(captor.getValue().voucherFileIds()).containsExactly("file-proof");
        assertThat(captor.getValue().note()).isEqualTo("到账");
    }

    @Test
    @DisplayName("PUT /donations/{no}/confirm 进度不一致时透传 40106")
    void confirm_returns_40106() throws Exception {
        AuthContext.set(admin());
        when(donationService.confirm(eq("DN202611210031"), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.DONATION_NOT_VERIFIED));

        mvc.perform(put("/api/v1/donations/DN202611210031/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40106));
    }

    @Test
    @DisplayName("PUT /donations/batch 解析 ids，返回部分成功明细")
    void batch_returns_partial_result() throws Exception {
        AuthContext.set(admin());
        when(donationService.confirmBatch(any(), any())).thenReturn(new BatchConfirmResult(
                1, List.of(new BatchFailure("DN202611210404", 30410, "捐赠记录不存在"))));

        mvc.perform(put("/api/v1/donations/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"DN202611210031\",\"DN202611210404\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed[0].id").value("DN202611210404"))
                .andExpect(jsonPath("$.data.failed[0].code").value(30410));

        ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(donationService).confirmBatch(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactly("DN202611210031", "DN202611210404");
    }

    private CurrentUser student() {
        return new CurrentUser(9001L, "U2026001", "小林", "STUDENT",
                Set.of("STUDENT"), "软件工程", "jwt-1", Instant.parse("2026-11-21T02:31:00Z"));
    }

    private CurrentUser admin() {
        return new CurrentUser(9101L, "U2026002", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤", "jwt-2", Instant.parse("2026-11-21T02:31:00Z"));
    }
}
