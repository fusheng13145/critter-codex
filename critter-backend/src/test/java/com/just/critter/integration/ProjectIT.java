package com.just.critter.integration;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.service.fund.CreatedProject;
import com.just.critter.service.fund.ProjectAuditCommand;
import com.just.critter.service.fund.ProjectAuditResult;
import com.just.critter.service.fund.ProjectDetail;
import com.just.critter.service.fund.ProjectDraftCommand;
import com.just.critter.service.fund.ProjectService;
import com.just.critter.service.fund.ProjectFilter;
import com.just.critter.service.fund.ProjectSummary;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T2.4 真库验收：项目与预算落库、预算 40101、提交审核、审核上架、公开详情。
 * 使用独立 critter_codex_it（当前 Flyway V12），事务回滚，不留下测试数据。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/critter_codex_it"
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=${CRITTER_MYSQL_USER:root}",
        "spring.datasource.password=${CRITTER_MYSQL_PASSWORD:}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "CRITTER_MYSQL_IT", matches = "true")
@Transactional
class ProjectIT extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("创建草稿：project + project_budget 落库，金额与初始汇总正确")
    void create_persists_project_and_budget() {
        CreatedProject created = service.create(command(false, 300_000), admin());

        assertThat(created.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(created.projectNo()).startsWith("PRJ-2026-");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT project_no, name, target_type, target_amount, raised_amount, spent_amount, status, deleted "
                        + "FROM project WHERE project_no = ?", created.projectNo());
        assertThat(row.get("name")).isEqualTo("冬季流浪猫医疗基金");
        assertThat(row.get("target_type")).isEqualTo("ANIMAL");
        assertThat(((Number) row.get("target_amount")).intValue()).isEqualTo(300_000);
        assertThat(((Number) row.get("raised_amount")).intValue()).isZero();
        assertThat(((Number) row.get("spent_amount")).intValue()).isZero();
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat((Boolean) row.get("deleted")).isFalse();

        List<Map<String, Object>> budgets = jdbc.queryForList(
                "SELECT subject, amount FROM project_budget WHERE project_id = "
                        + "(SELECT id FROM project WHERE project_no = ?) ORDER BY id", created.projectNo());
        assertThat(budgets).hasSize(2);
        assertThat(budgets).extracting(r -> r.get("subject")).containsExactly("检查", "药品");
        int total = budgets.stream().mapToInt(r -> ((Number) r.get("amount")).intValue()).sum();
        assertThat(total).isEqualTo(300_000);
    }

    @Test
    @DisplayName("预算 3200 / 目标 3000 被拒，40101 且不落项目行")
    void budget_over_target_rolls_back() {
        assertThatThrownBy(() -> service.create(command(false, 320_000), admin()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDS_TARGET.getCode());
                    assertThat(ex.getData()).isInstanceOf(Map.class);
                });

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project WHERE name = ? AND target_amount = 300000",
                Integer.class, "冬季流浪猫医疗基金");
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("提交审核 -> 通过 -> FUNDING；公开详情带预算与进度")
    void submit_audit_and_public_detail() {
        CreatedProject created = service.create(command(true, 300_000), admin());
        assertThat(created.status()).isEqualTo(ProjectStatus.PENDING_AUDIT);

        ProjectAuditResult audit = service.audit(created.projectNo(),
                new ProjectAuditCommand("APPROVE", null), admin());
        assertThat(audit.status()).isEqualTo(ProjectStatus.FUNDING);
        assertThat(audit.auditorId()).isEqualTo(9001L);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, auditor_id, audit_reason FROM project WHERE project_no = ?",
                created.projectNo());
        assertThat(row.get("status")).isEqualTo("FUNDING");
        assertThat(((Number) row.get("auditor_id")).longValue()).isEqualTo(9001L);
        assertThat(row.get("audit_reason")).isNull();

        ProjectDetail detail = service.detail(created.projectNo(), false);
        assertThat(detail.status()).isEqualTo(ProjectStatus.FUNDING);
        assertThat(detail.budget()).hasSize(2);
        assertThat(detail.budgetTotal()).isEqualTo(300_000);
        assertThat(detail.progressPercent()).isZero();
        assertThat(detail.donationCount()).isZero();
        assertThat(detail.allowedActions()).isEmpty();
    }

    @Test
    @DisplayName("公开列表：FUNDING 可见，DRAFT 即使显式筛选也不返回")
    void public_list_hides_draft() {
        CreatedProject draft = service.create(command(false, 300_000), admin());
        CreatedProject funded = service.create(command(true, 300_000), admin());
        service.audit(funded.projectNo(), new ProjectAuditCommand("APPROVE", null), admin());

        PageResult<ProjectSummary> page = service.search(
                new ProjectFilter(List.of(ProjectStatus.DRAFT, ProjectStatus.FUNDING), null, null),
                new PageQuery(1, 20), false);

        assertThat(page.getList()).extracting(ProjectSummary::projectNo)
                .contains(funded.projectNo())
                .doesNotContain(draft.projectNo());
        assertThat(page.getList()).extracting(ProjectSummary::status)
                .doesNotContain(ProjectStatus.DRAFT);
    }

    private ProjectDraftCommand command(boolean submit, int secondBudget) {
        return new ProjectDraftCommand(
                "冬季流浪猫医疗基金", "用于校园流浪猫绝育与伤病治疗，费用凭票据入账。",
                "ANIMAL", 300_000, LocalDate.parse("2026-11-21"), LocalDate.parse("2026-12-31"),
                List.of(new ProjectDraftCommand.BudgetItem("检查", 120_000, "血常规与影像"),
                        new ProjectDraftCommand.BudgetItem("药品", secondBudget - 120_000, null)),
                null, submit);
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }
}
