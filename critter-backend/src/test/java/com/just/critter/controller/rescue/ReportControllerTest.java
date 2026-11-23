package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.AcceptedReport;
import com.just.critter.service.rescue.AcceptReportCommand;
import com.just.critter.service.rescue.ClosedReport;
import com.just.critter.service.rescue.CreateReportCommand;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportDetail;
import com.just.critter.service.rescue.ReportFilter;
import com.just.critter.service.rescue.ReportService;
import com.just.critter.service.rescue.ReportSummary;
import com.just.critter.service.rescue.RescueOrderService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T2.1 接口契约：登录可提交、成功壳字段、未登录 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private ReportService reportService;

    @MockBean
    private RescueOrderService rescueOrderService;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(new CurrentUser(1001L, "2023019001", "学生", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jti", null));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("POST /reports 返回编号、状态、时限与去重标记")
    void create_returns_contract_fields() throws Exception {
        when(reportService.create(any(), eq(1001L))).thenReturn(new CreatedReport(
                "RP20261121007", ReportStatus.PENDING,
                OffsetDateTime.parse("2026-11-22T10:31:00+08:00"), false, null));

        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"INJURED","pointId":7,"photos":["F1","F2"],
                                 "description":"右后腿受伤无法正常站立",
                                 "animalCode":"CAT-2026-001","contact":"13800000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reportNo").value("RP20261121007"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.deadline").value("2026-11-22T10:31:00+08:00"))
                .andExpect(jsonPath("$.data.duplicated").value(false));

        ArgumentCaptor<CreateReportCommand> captor = ArgumentCaptor.forClass(CreateReportCommand.class);
        verify(reportService).create(captor.capture(), eq(1001L));
        CreateReportCommand command = captor.getValue();
        assertThat(command.type()).isEqualTo("INJURED");
        assertThat(command.pointId()).isEqualTo(7L);
        assertThat(command.photos()).containsExactly("F1", "F2");
        assertThat(command.description()).isEqualTo("右后腿受伤无法正常站立");
        assertThat(command.animalCode()).isEqualTo("CAT-2026-001");
        assertThat(command.contact()).isEqualTo("13800000000");
    }

    @Test
    @DisplayName("GET /reports 按状态/类型/时限过滤并返回分页")
    void list_returns_page_and_parses_filters() throws Exception {
        when(reportService.search(any(), any())).thenReturn(PageResult.of(List.of(
                new ReportSummary(5L, "RP20261121007", ReportType.INJURED, 7L, "东区宿舍七号楼",
                        "右后腿受伤无法正常站立", List.of("F1"), ReportStatus.PENDING, true,
                        java.time.LocalDateTime.parse("2026-11-20T10:31:00"),
                        OffsetDateTime.parse("2026-11-21T10:31:00+08:00"), 1001L,
                        null, null, false, false, null)), 1, 10, 1));

        mvc.perform(get("/api/v1/reports")
                        .param("status", "PENDING")
                        .param("type", "INJURED")
                        .param("pointId", "7")
                        .param("overdue", "true")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].reportNo").value("RP20261121007"))
                .andExpect(jsonPath("$.data.list[0].overdue").value(true))
                .andExpect(jsonPath("$.data.page.total").value(1));

        ArgumentCaptor<ReportFilter> filterCaptor = ArgumentCaptor.forClass(ReportFilter.class);
        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);
        verify(reportService).search(filterCaptor.capture(), pageCaptor.capture());
        assertThat(filterCaptor.getValue().statuses()).containsExactly(ReportStatus.PENDING);
        assertThat(filterCaptor.getValue().type()).isEqualTo(ReportType.INJURED);
        assertThat(filterCaptor.getValue().pointId()).isEqualTo(7L);
        assertThat(filterCaptor.getValue().overdue()).isTrue();
        assertThat(pageCaptor.getValue().getPage()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /reports/{id} 返回详情")
    void detail_returns_report() throws Exception {
        when(reportService.detail(eq(5L), any())).thenReturn(new ReportDetail(
                5L, "RP20261121007", ReportType.INJURED, 7L, "东区宿舍七号楼",
                "右后腿受伤无法正常站立", List.of("F1"), ReportStatus.PENDING, false,
                java.time.LocalDateTime.parse("2026-11-20T10:31:00"),
                OffsetDateTime.parse("2026-11-21T10:31:00+08:00"),
                1001L, null, null, null, null,
                null, null, false, false, null));

        mvc.perform(get("/api/v1/reports/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reportNo").value("RP20261121007"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /reports/{id}/accept 受理并返回工单号")
    void accept_returns_order_no() throws Exception {
        when(rescueOrderService.acceptReport(eq(5L), any(), any())).thenReturn(new AcceptedReport(
                "RO20261121001", "RP20261121007", ReportStatus.PROCESSING, OrderStatus.PROCESSING,
                OffsetDateTime.parse("2026-11-22T10:31:00+08:00")));

        mvc.perform(post("/api/v1/reports/5/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handlerId":3001,"animalCode":"CAT-2026-001","note":"已联系处置人"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("RO20261121001"))
                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"));

        ArgumentCaptor<AcceptReportCommand> captor = ArgumentCaptor.forClass(AcceptReportCommand.class);
        verify(rescueOrderService).acceptReport(eq(5L), captor.capture(), any());
        assertThat(captor.getValue().handlerId()).isEqualTo(3001L);
        assertThat(captor.getValue().animalCode()).isEqualTo("CAT-2026-001");
        assertThat(captor.getValue().note()).isEqualTo("已联系处置人");
    }

    @Test
    @DisplayName("PUT /reports/{id}/close 关闭无效上报")
    void close_returns_closed_status() throws Exception {
        when(reportService.close(eq(5L), any(), anyBoolean()))
                .thenReturn(new ClosedReport("RP20261121007", ReportStatus.CLOSED, "重复上报", true, null));

        mvc.perform(put("/api/v1/reports/5/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"重复上报\",\"invalid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.invalid").value(true))
                .andExpect(jsonPath("$.data.closeReason").value("重复上报"));
    }

    @Test
    @DisplayName("未登录提交返回 20001/401")
    void anonymous_create_is_rejected() throws Exception {
        AuthContext.clear();

        mvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"INJURED","pointId":7,"photos":["F1"],
                                 "description":"右后腿受伤无法正常站立"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }
}
