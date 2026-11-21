package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;
import com.just.critter.domain.fund.ProjectCodeGenerator;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.ProjectBudget;
import com.just.critter.mapper.ProjectBudgetMapper;
import com.just.critter.mapper.ProjectDonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2.4 验收：项目创建/预算校验/提交/审核/公开详情/进度。
 * 金额一律按分；预算合计超过目标金额必须 40101。
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectBudgetMapper budgetMapper;
    @Mock
    private ProjectDonationMapper donationMapper;
    @Mock
    private AuditLogService auditLog;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectMapper, budgetMapper, donationMapper,
                new ProjectCodeGenerator((bizType, year) -> 7, CLOCK), auditLog, CLOCK);
    }

    @Test
    @DisplayName("创建：草稿项目 + 预算明细落库，金额/编号/初始汇总正确")
    void create_persists_draft_and_budgets() {
        doAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(11L);
            return 1;
        }).when(projectMapper).insert(any(Project.class));

        CreatedProject created = service.create(command(false), admin());

        assertThat(created.projectNo()).isEqualTo("PRJ-2026-007");
        assertThat(created.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(created.createdAt()).isEqualTo(OffsetDateTime.parse("2026-11-21T10:31:00+08:00"));

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).insert(projectCaptor.capture());
        Project saved = projectCaptor.getValue();
        assertThat(saved.getProjectNo()).isEqualTo("PRJ-2026-007");
        assertThat(saved.getName()).isEqualTo("冬季流浪猫医疗基金");
        assertThat(saved.getTargetType()).isEqualTo(ProjectTargetType.ANIMAL);
        assertThat(saved.getTargetAmount()).isEqualTo(300_000);
        assertThat(saved.getRaisedAmount()).isZero();
        assertThat(saved.getSpentAmount()).isZero();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(saved.getDeleted()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo(9001L);

        // MyBatis-Plus 的单条 insert 逐条调用，下面直接按数量与字段断言
        verify(budgetMapper, org.mockito.Mockito.times(2)).insert(any(ProjectBudget.class));
        verify(auditLog).success(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("创建：预算 3200 元 / 目标 3000 元被拒，40101 带回差额")
    void create_rejects_budget_over_target() {
        ProjectDraftCommand command = new ProjectDraftCommand(
                "冬季流浪猫医疗基金", "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                "ANIMAL", 300_000, LocalDate.parse("2026-11-21"), LocalDate.parse("2026-12-31"),
                List.of(new ProjectDraftCommand.BudgetItem("检查", 200_000, null),
                        new ProjectDraftCommand.BudgetItem("药品", 120_000, null)),
                null, false);

        assertThatThrownBy(() -> service.create(command, admin()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDS_TARGET.getCode());
                    assertThat(ex.getData()).isInstanceOf(Map.class);
                    Map<?, ?> data = (Map<?, ?>) ex.getData();
                    assertThat(data.get("targetAmount")).isEqualTo(300_000);
                    assertThat(data.get("budgetTotal")).isEqualTo(320_000);
                    assertThat(data.get("excessAmount")).isEqualTo(20_000);
                });

        verify(projectMapper, never()).insert(any(Project.class));
        verify(budgetMapper, never()).insert(any(ProjectBudget.class));
    }

    @Test
    @DisplayName("创建：submit=true 直接进入 PENDING_AUDIT，供审核上架")
    void create_with_submit_enters_pending_audit() {
        doAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(11L);
            return 1;
        }).when(projectMapper).insert(any(Project.class));

        CreatedProject created = service.create(command(true), admin());

        assertThat(created.status()).isEqualTo(ProjectStatus.PENDING_AUDIT);
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProjectStatus.PENDING_AUDIT);
    }

    @Test
    @DisplayName("审核通过：PENDING_AUDIT -> FUNDING，写审核人")
    void audit_approve_moves_to_funding() {
        Project project = project(ProjectStatus.PENDING_AUDIT, 11L);
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project);

        ProjectAuditResult result = service.audit("PRJ-2026-007",
                new ProjectAuditCommand("APPROVE", null), admin());

        assertThat(result.status()).isEqualTo(ProjectStatus.FUNDING);
        assertThat(result.auditorId()).isEqualTo(9001L);
        assertThat(project.getAuditorId()).isEqualTo(9001L);
        verify(projectMapper).updateById(project);
        verify(auditLog).successAs(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("审核驳回：必须填理由，PENDING_AUDIT -> REJECTED")
    void audit_reject_requires_reason() {
        Project project = project(ProjectStatus.PENDING_AUDIT, 11L);
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project);

        ProjectAuditResult result = service.audit("PRJ-2026-007",
                new ProjectAuditCommand("REJECT", "预算依据不足"), admin());

        assertThat(result.status()).isEqualTo(ProjectStatus.REJECTED);
        assertThat(result.auditReason()).isEqualTo("预算依据不足");
        assertThat(project.getAuditReason()).isEqualTo("预算依据不足");
    }

    @Test
    @DisplayName("审核：非 PENDING_AUDIT 返回 40108")
    void audit_rejects_wrong_state() {
        when(projectMapper.selectByProjectNo("PRJ-2026-007"))
                .thenReturn(project(ProjectStatus.DRAFT, 11L));

        assertThatThrownBy(() -> service.audit("PRJ-2026-007",
                new ProjectAuditCommand("APPROVE", null), admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PROJECT_STATE_INVALID.getCode()));
        verify(projectMapper, never()).updateById(any(Project.class));
    }

    @Test
    @DisplayName("修改被驳回项目并重新提交：预算整体替换，进入待审核")
    void update_rejected_and_resubmit() {
        Project project = project(ProjectStatus.REJECTED, 11L);
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project);
        when(budgetMapper.delete(any())).thenReturn(2);

        ProjectDetail detail = service.update("PRJ-2026-007", command(true), admin());

        assertThat(detail.status()).isEqualTo(ProjectStatus.PENDING_AUDIT);
        assertThat(detail.budget()).hasSize(2);
        verify(budgetMapper).delete(any());
        verify(budgetMapper, org.mockito.Mockito.times(2)).insert(any(ProjectBudget.class));
        assertThat(project.getAuditReason()).isNull();
    }

    @Test
    @DisplayName("详情：公开访问隐藏草稿；管理员可见")
    void detail_hides_draft_from_public() {
        Project draft = project(ProjectStatus.DRAFT, 11L);
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(draft);

        assertThatThrownBy(() -> service.detail("PRJ-2026-007", false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("详情：进度百分比、剩余天数、预算与已入账笔数")
    void detail_calculates_progress() {
        Project project = project(ProjectStatus.FUNDING, 11L);
        project.setRaisedAmount(150_000);
        project.setStartDate(LocalDate.parse("2026-11-21"));
        project.setEndDate(LocalDate.parse("2026-12-31"));
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project);
        when(budgetMapper.selectList(any())).thenReturn(List.of(
                budget("检查", 120_000, "血常规与影像"),
                budget("药品", 180_000, null)));
        when(donationMapper.countVerifiedByProjectIds(anyList()))
                .thenReturn(List.of(Map.of("projectId", 11L, "donationCount", 3L)));
        when(donationMapper.recentVerified(11L, 10)).thenReturn(List.of());

        ProjectDetail detail = service.detail("PRJ-2026-007", false);

        assertThat(detail.progressPercent()).isEqualTo(50);
        assertThat(detail.daysRemaining()).isEqualTo(40);
        assertThat(detail.budget()).extracting(ProjectBudgetItem::subject).containsExactly("检查", "药品");
        assertThat(detail.donationCount()).isEqualTo(3);
    }

    private ProjectDraftCommand command(boolean submit) {
        return new ProjectDraftCommand(
                "冬季流浪猫医疗基金", "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                "ANIMAL", 300_000, LocalDate.parse("2026-11-21"), LocalDate.parse("2026-12-31"),
                List.of(new ProjectDraftCommand.BudgetItem("检查", 120_000, "血常规与影像"),
                        new ProjectDraftCommand.BudgetItem("药品", 180_000, null)),
                null, submit);
    }

    private Project project(ProjectStatus status, long id) {
        Project project = new Project();
        project.setId(id);
        project.setProjectNo("PRJ-2026-007");
        project.setName("冬季流浪猫医疗基金");
        project.setPurpose("用于校园流浪猫绝育与伤病治疗，费用凭票据入账。");
        project.setTargetType(ProjectTargetType.ANIMAL);
        project.setTargetAmount(300_000);
        project.setRaisedAmount(0);
        project.setSpentAmount(0);
        project.setStartDate(LocalDate.parse("2026-11-21"));
        project.setEndDate(LocalDate.parse("2026-12-31"));
        project.setStatus(status);
        project.setDeleted(0);
        project.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        project.setUpdatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        project.setCreatedBy(9001L);
        return project;
    }

    private ProjectBudget budget(String subject, int amount, String note) {
        ProjectBudget budget = new ProjectBudget();
        budget.setProjectId(11L);
        budget.setSubject(subject);
        budget.setAmount(amount);
        budget.setNote(note);
        return budget;
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }
}
