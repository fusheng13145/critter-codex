package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.service.rescue.CreateReportCommand;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
