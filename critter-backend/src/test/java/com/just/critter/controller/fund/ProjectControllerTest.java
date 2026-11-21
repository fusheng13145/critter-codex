package com.just.critter.controller.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;
import com.just.critter.service.fund.CreatedProject;
import com.just.critter.service.fund.ProjectAuditCommand;
import com.just.critter.service.fund.ProjectAuditResult;
import com.just.critter.service.fund.ProjectDetail;
import com.just.critter.service.fund.ProjectDraftCommand;
import com.just.critter.service.fund.ProjectFilter;
import com.just.critter.service.fund.ProjectService;
import com.just.critter.service.fund.ProjectSummary;
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

import java.time.LocalDate;
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
 * T2.4 接口契约：公开列表/详情、创建项目、40101、审核与修改提交。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private ProjectService projectService;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(admin());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("POST /projects 返回项目编号与 DRAFT，解析预算与服务字段")
    void create_returns_project_and_parses_budget() throws Exception {
        when(projectService.create(any(), any())).thenReturn(new CreatedProject(
                "PRJ-2026-007", ProjectStatus.DRAFT,
                OffsetDateTime.parse("2026-11-21T10:31:00+08:00")));

        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "冬季流浪猫医疗基金",
                                  "purpose": "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                                  "targetType": "ANIMAL",
                                  "targetAmount": 300000,
                                  "startDate": "2026-11-21",
                                  "endDate": "2026-12-31",
                                  "budget": [
                                    {"subject": "检查", "amount": 120000, "note": "血常规与影像"},
                                    {"subject": "药品", "amount": 180000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.projectNo").value("PRJ-2026-007"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        ArgumentCaptor<ProjectDraftCommand> captor = ArgumentCaptor.forClass(ProjectDraftCommand.class);
        verify(projectService).create(captor.capture(), any());
        ProjectDraftCommand command = captor.getValue();
        assertThat(command.name()).isEqualTo("冬季流浪猫医疗基金");
        assertThat(command.targetType()).isEqualTo("ANIMAL");
        assertThat(command.targetAmount()).isEqualTo(300_000);
        assertThat(command.startDate()).isEqualTo(LocalDate.parse("2026-11-21"));
        assertThat(command.budget()).hasSize(2);
        assertThat(command.budget().getFirst().subject()).isEqualTo("检查");
        assertThat(command.submit()).isFalse();
    }

    @Test
    @DisplayName("POST /projects 预算超目标返回 40101 与差额")
    void create_budget_over_target_returns_40101() throws Exception {
        when(projectService.create(any(), any())).thenThrow(new BusinessException(
                ErrorCode.BUDGET_EXCEEDS_TARGET,
                java.util.Map.of("targetAmount", 300_000, "budgetTotal", 320_000, "excessAmount", 20_000)));

        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "冬季流浪猫医疗基金",
                                  "purpose": "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                                  "targetType": "ANIMAL",
                                  "targetAmount": 300000,
                                  "startDate": "2026-11-21",
                                  "endDate": "2026-12-31",
                                  "budget": [{"subject": "检查", "amount": 320000}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.data.excessAmount").value(20_000));
    }

    @Test
    @DisplayName("GET /projects 公开列表解析状态/类型筛选，管理员可见全部")
    void list_parses_filters_and_admin_visibility() throws Exception {
        when(projectService.search(any(), any(), eq(true)))
                .thenReturn(PageResult.of(List.of(summary()), 1, 10, 1));

        mvc.perform(get("/api/v1/projects")
                        .param("status", "FUNDING,PENDING_SETTLE")
                        .param("targetType", "ANIMAL")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].projectNo").value("PRJ-2026-007"))
                .andExpect(jsonPath("$.data.list[0].progressPercent").value(50))
                .andExpect(jsonPath("$.data.page.total").value(1));

        ArgumentCaptor<ProjectFilter> filter = ArgumentCaptor.forClass(ProjectFilter.class);
        ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
        verify(projectService).search(filter.capture(), page.capture(), eq(true));
        assertThat(filter.getValue().statuses())
                .containsExactly(ProjectStatus.FUNDING, ProjectStatus.PENDING_SETTLE);
        assertThat(filter.getValue().targetType()).isEqualTo(ProjectTargetType.ANIMAL);
        assertThat(page.getValue().getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /projects 匿名访问按公开可见性调用服务")
    void list_anonymous_uses_public_visibility() throws Exception {
        AuthContext.clear();
        when(projectService.search(any(), any(), eq(false)))
                .thenReturn(PageResult.of(List.of(), 1, 20, 0));

        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page.total").value(0));

        verify(projectService).search(any(), any(), eq(false));
    }

    @Test
    @DisplayName("GET /projects/{no} 匿名可看公开项目详情")
    void detail_public() throws Exception {
        AuthContext.clear();
        when(projectService.detail("PRJ-2026-007", false)).thenReturn(detail());

        mvc.perform(get("/api/v1/projects/PRJ-2026-007"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.projectNo").value("PRJ-2026-007"))
                .andExpect(jsonPath("$.data.budget[0].subject").value("检查"))
                .andExpect(jsonPath("$.data.progressPercent").value(50));
    }

    @Test
    @DisplayName("PUT /projects/{no}/audit 解析驳回决定与理由")
    void audit_parses_decision_and_reason() throws Exception {
        when(projectService.audit(eq("PRJ-2026-007"), any(), any())).thenReturn(
                new ProjectAuditResult("PRJ-2026-007", ProjectStatus.REJECTED,
                        "预算依据不足", 9001L,
                        OffsetDateTime.parse("2026-11-21T10:31:00+08:00"), List.of("RESUBMIT")));

        mvc.perform(put("/api/v1/projects/PRJ-2026-007/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "REJECT", "reason": "预算依据不足"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.auditReason").value("预算依据不足"));

        ArgumentCaptor<ProjectAuditCommand> command = ArgumentCaptor.forClass(ProjectAuditCommand.class);
        verify(projectService).audit(eq("PRJ-2026-007"), command.capture(), any());
        assertThat(command.getValue().decision()).isEqualTo("REJECT");
        assertThat(command.getValue().reason()).isEqualTo("预算依据不足");
    }

    @Test
    @DisplayName("PUT /projects/{no} 修改草稿并提交审核")
    void update_with_submit() throws Exception {
        when(projectService.update(eq("PRJ-2026-007"), any(), any())).thenReturn(detail());

        mvc.perform(put("/api/v1/projects/PRJ-2026-007")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "冬季流浪猫医疗基金",
                                  "purpose": "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                                  "targetType": "ANIMAL",
                                  "targetAmount": 300000,
                                  "startDate": "2026-11-21",
                                  "endDate": "2026-12-31",
                                  "budget": [{"subject": "检查", "amount": 120000},
                                             {"subject": "药品", "amount": 180000}],
                                  "submit": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectNo").value("PRJ-2026-007"));

        ArgumentCaptor<ProjectDraftCommand> command = ArgumentCaptor.forClass(ProjectDraftCommand.class);
        verify(projectService).update(eq("PRJ-2026-007"), command.capture(), any());
        assertThat(command.getValue().submit()).isTrue();
        assertThat(command.getValue().budget()).hasSize(2);
    }

    @Test
    @DisplayName("POST /projects 学生角色被拒，避免可选鉴权路径绕过角色校验")
    void create_student_forbidden() throws Exception {
        AuthContext.set(student());

        mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "冬季流浪猫医疗基金",
                                  "purpose": "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                                  "targetType": "ANIMAL",
                                  "targetAmount": 300000,
                                  "startDate": "2026-11-21",
                                  "endDate": "2026-12-31",
                                  "budget": [{"subject": "检查", "amount": 120000}]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));

        verify(projectService, org.mockito.Mockito.never()).create(any(), any());
    }

    private ProjectSummary summary() {
        return new ProjectSummary("PRJ-2026-007", "冬季流浪猫医疗基金", ProjectTargetType.ANIMAL,
                300_000, 150_000, 0, 50, LocalDate.parse("2026-11-21"),
                LocalDate.parse("2026-12-31"), 40, ProjectStatus.FUNDING, 3,
                null, OffsetDateTime.parse("2026-11-21T10:31:00+08:00"));
    }

    private ProjectDetail detail() {
        return new ProjectDetail("PRJ-2026-007", "冬季流浪猫医疗基金",
                "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。", ProjectTargetType.ANIMAL,
                300_000, 150_000, 0, 50, LocalDate.parse("2026-11-21"),
                LocalDate.parse("2026-12-31"), 40, ProjectStatus.FUNDING, null, null, null,
                3, 300_000,
                List.of(new com.just.critter.service.fund.ProjectBudgetItem("检查", 120_000, "血常规与影像"),
                        new com.just.critter.service.fund.ProjectBudgetItem("药品", 180_000, null)),
                List.of(), OffsetDateTime.parse("2026-11-21T10:31:00+08:00"),
                OffsetDateTime.parse("2026-11-21T10:31:00+08:00"), List.of());
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }

    private CurrentUser student() {
        return new CurrentUser(1001L, "2026000001", "学生", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jti", null);
    }
}
