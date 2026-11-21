package com.just.critter.integration;

import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.Gender;
import com.just.critter.domain.enums.ExpenseStatus;
import com.just.critter.job.fund.ProjectDeadlineJob;
import com.just.critter.job.fund.ProjectDeadlineScanResult;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.CreateAnimalCommand;
import com.just.critter.service.fund.*;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T4.1 并发与事务验收：编号发号、认养占用、捐赠入账、支出复核、到期扫描与自复核拦截。
 *
 * <p>这些用例必须提交真实事务才能让多条线程互相看见，因此类级别不加 @Transactional；
 * 每个用例自行准备数据并在 finally 中清理。仅在 CRITTER_MYSQL_IT=true 时运行。</p>
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
class ConcurrencyAndTransactionIT extends MySqlIntegrationTestSupport {

    @Autowired
    private AnimalService animalService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private DonationService donationService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private AdoptionAuditService adoptionAuditService;
    @Autowired
    private ProjectDeadlineJob projectDeadlineJob;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("并发建档：8 条线程同时取号，编号不重复、不跳号")
    void concurrentAnimalCreation_neverDuplicatesCode() throws Exception {
        long pointId = pointId();
        List<String> createdCodes = new ArrayList<>();
        try {
            RunResult<String> run = runConcurrently(8, index -> animalService.create(
                    new CreateAnimalCommand(AnimalCategory.CAT, Gender.FEMALE, "橘白",
                            2, "并发建档 " + index, pointId, List.of("IT-PHOTO"), null, null),
                    ADMIN_ID).code());
            createdCodes.addAll(run.successes());
            assertThat(run.failures()).isEmpty();
            assertThat(run.successes()).hasSize(8);
            assertThat(run.successes()).doesNotHaveDuplicates();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM animal WHERE code IN (" + placeholders(createdCodes.size()) + ")",
                    Integer.class, createdCodes.toArray())).isEqualTo(8);
        } finally {
            createdCodes.forEach(code -> jdbc.update("DELETE FROM animal WHERE code = ?", code));
        }
    }

    @Test
    @DisplayName("并发认养审核：同一动物两张待审单，仅一单进入 PENDING_PAYMENT")
    void concurrentAdoptionAudit_onlyOneWins() throws Exception {
        long pointId = pointId();
        String animalCode = animalService.create(new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.MALE, "狸花", 1, "并发认养测试", pointId,
                List.of("IT-PHOTO"), null, null), ADMIN_ID).code();
        Long animalId = jdbc.queryForObject("SELECT id FROM animal WHERE code = ?", Long.class, animalCode);
        Long planId = jdbc.queryForList(
                "SELECT id FROM adoption_plan WHERE enabled = 1 ORDER BY id LIMIT 1", Long.class)
                .stream().findFirst().orElse(null);
        boolean planCreated = false;
        if (planId == null) {
            jdbc.update("""
                    INSERT INTO adoption_plan (plan_name, period, amount, benefits, enabled)
                    VALUES ('IT并发认养方案', 'MONTH', 10000, '测试方案', 1)
                    """);
            planId = jdbc.queryForObject(
                    "SELECT id FROM adoption_plan WHERE plan_name = 'IT并发认养方案'", Long.class);
            planCreated = true;
        }
        String firstNo = "AD20261121001";
        String secondNo = "AD20261121002";
        insertAdoption(firstNo, 1L, animalId, animalCode, planId);
        insertAdoption(secondNo, 2L, animalId, animalCode, planId);
        try {
            RunResult<String> run = runConcurrently(2, index -> adoptionAuditService.audit(
                    index == 0 ? firstNo : secondNo,
                    new AdoptionAuditCommand(AdoptionDecision.APPROVE, "资料齐全，同意认养", null),
                    ADMIN_ID).adoptionNo());

            assertThat(run.successes()).hasSize(1);
            assertThat(run.failures()).hasSize(1);
            assertThat(run.failures().getFirst()).isInstanceOf(BusinessException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM adoption WHERE animal_id = ? AND status = 'PENDING_PAYMENT'",
                    Integer.class, animalId)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM adoption WHERE adoption_no IN (?, ?)", firstNo, secondNo);
            jdbc.update("DELETE FROM animal WHERE code = ?", animalCode);
            if (planCreated) {
                jdbc.update("DELETE FROM adoption_plan WHERE plan_name = 'IT并发认养方案'");
            }
        }
    }

    @Test
    @DisplayName("并发核对同一笔捐赠：只入账一次，项目进度只加一次")
    void concurrentDonationConfirm_creditsOnce() throws Exception {
        String projectNo = fundingProject("T4.1 并发捐赠");
        CreatedDonation donation = donationService.create(
                new CreateDonationCommand(projectNo, 20_000, false, "并发核对", List.of("IT-VOUCHER")),
                STUDENT);
        Long projectId = projectId(projectNo);
        try {
            RunResult<String> run = runConcurrently(8, index -> donationService.confirm(
                    donation.donationNo(),
                    new ConfirmDonationCommand(20_000, null, List.of("IT-PROOF"), "并发到账"),
                    ADMIN).donationNo());

            assertThat(run.successes()).hasSize(1);
            assertThat(run.failures()).hasSize(7);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM donation WHERE donation_no = ?", String.class, donation.donationNo()))
                    .isEqualTo("VERIFIED");
            assertThat(jdbc.queryForObject(
                    "SELECT raised_amount FROM project WHERE project_no = ?", Integer.class, projectNo))
                    .isEqualTo(20_000);
        } finally {
            jdbc.update("DELETE FROM donation WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    @Test
    @DisplayName("并发复核同一笔支出：只通过一次，项目已支出只加一次")
    void concurrentExpenseReview_approvesOnce() throws Exception {
        String projectNo = fundingProject("T4.1 并发支出");
        Long projectId = projectId(projectNo);
        jdbc.update("UPDATE project SET raised_amount = 500000 WHERE id = ?", projectId);
        String animalCode = animalService.create(new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.FEMALE, "白猫", 3, "支出关联动物", pointId(),
                List.of("IT-PHOTO"), null, null), ADMIN_ID).code();
        CreatedExpense expense = expenseService.create(new CreateExpenseCommand(
                projectNo, animalCode, "并发复核测试", 50_000, "校园医院",
                OffsetDateTime.now(), List.of("IT-VOUCHER")), ADMIN);
        try {
            RunResult<String> run = runConcurrently(6, index -> expenseService.review(
                    expense.expenseNo(),
                    new ExpenseReviewCommand(ExpenseDecision.APPROVE, "票据齐全"),
                    ADMIN_B).expenseNo());

            assertThat(run.successes()).hasSize(1);
            assertThat(run.failures()).hasSize(5);
            assertThat(run.failures()).allMatch(error -> error instanceof BusinessException);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM expense WHERE expense_no = ?", String.class, expense.expenseNo()))
                    .isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject(
                    "SELECT spent_amount FROM project WHERE project_no = ?", Integer.class, projectNo))
                    .isEqualTo(50_000);
        } finally {
            jdbc.update("DELETE FROM expense WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM animal WHERE code = ?", animalCode);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    @Test
    @DisplayName("并发到期扫描：同一项目只迁移一次、只写一条审计")
    void concurrentDeadlineScan_isIdempotent() throws Exception {
        String projectNo = fundingProject("T4.1 到期扫描", LocalDate.parse("2026-11-15"));
        Long projectId = projectId(projectNo);
        jdbc.update("UPDATE project SET raised_amount = 350000 WHERE id = ?", projectId);
        try {
            RunResult<ProjectDeadlineScanResult> run = runConcurrently(4, index -> projectDeadlineJob.scan());

            assertThat(run.successes()).hasSize(4);
            int pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE project_no = ? AND status = 'PENDING_SETTLE'",
                    Integer.class, projectNo);
            assertThat(pending).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_log WHERE action = 'PROJECT_DEADLINE_REACHED'"
                            + " AND biz_id = ?", Integer.class, projectNo)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM sys_log WHERE biz_id = ?", projectNo);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    @Test
    @DisplayName("事务约束：登记人自复核被拒，换人复核后才入账")
    void selfReview_isRejected_thenAnotherAdminCanApprove() {
        String projectNo = fundingProject("T4.1 自复核拦截");
        Long projectId = projectId(projectNo);
        jdbc.update("UPDATE project SET raised_amount = 300000 WHERE id = ?", projectId);
        String animalCode = animalService.create(new CreateAnimalCommand(
                AnimalCategory.CAT, Gender.MALE, "黑猫", 2, "自复核测试", pointId(),
                List.of("IT-PHOTO"), null, null), ADMIN_ID).code();
        CreatedExpense expense = expenseService.create(new CreateExpenseCommand(
                projectNo, animalCode, "自复核拦截测试", 20_000, "校园医院",
                OffsetDateTime.now(), List.of("IT-VOUCHER")), ADMIN);
        try {
            assertThatThrownBy(() -> expenseService.review(expense.expenseNo(),
                    new ExpenseReviewCommand(ExpenseDecision.APPROVE, "本人复核"), ADMIN))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SELF_REVIEW_NOT_ALLOWED.getCode()));
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM expense WHERE expense_no = ?", String.class, expense.expenseNo()))
                    .isEqualTo(ExpenseStatus.PENDING_REVIEW.name());
            assertThat(jdbc.queryForObject(
                    "SELECT spent_amount FROM project WHERE project_no = ?", Integer.class, projectNo))
                    .isZero();

            expenseService.review(expense.expenseNo(),
                    new ExpenseReviewCommand(ExpenseDecision.APPROVE, "换人复核"), ADMIN_B);

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM expense WHERE expense_no = ?", String.class, expense.expenseNo()))
                    .isEqualTo(ExpenseStatus.APPROVED.name());
            assertThat(jdbc.queryForObject(
                    "SELECT spent_amount FROM project WHERE project_no = ?", Integer.class, projectNo))
                    .isEqualTo(20_000);
        } finally {
            jdbc.update("DELETE FROM expense WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM animal WHERE code = ?", animalCode);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    private String fundingProject(String name) {
        return fundingProject(name, LocalDate.parse("2026-12-31"));
    }

    private String fundingProject(String name, LocalDate endDate) {
        CreatedProject created = projectService.create(new ProjectDraftCommand(
                name, "T4.1 并发与事务验收项目，测试后清理。", "ANIMAL", 300_000,
                LocalDate.parse("2026-11-01"), endDate,
                List.of(new ProjectDraftCommand.BudgetItem("医疗", 300_000, null)),
                null, true), ADMIN);
        projectService.audit(created.projectNo(), new ProjectAuditCommand("APPROVE", null), ADMIN);
        return created.projectNo();
    }

    private long pointId() {
        return jdbc.queryForObject(
                "SELECT id FROM base_point WHERE enabled = 1 ORDER BY id LIMIT 1", Long.class);
    }

    private Long projectId(String projectNo) {
        return jdbc.queryForObject("SELECT id FROM project WHERE project_no = ?", Long.class, projectNo);
    }

    private void insertAdoption(String adoptionNo, long userId, long animalId,
                                String animalCode, long planId) {
        jdbc.update("""
                INSERT INTO adoption
                    (adoption_no, user_id, animal_id, animal_code, plan_id, amount, period, status,
                     contact, promise_accepted_at, created_at, updated_at, created_by)
                VALUES (?, ?, ?, ?, ?, 10000, 'MONTH', 'PENDING_AUDIT', '13800000000', NOW(), NOW(), NOW(), ?)
                """, adoptionNo, userId, animalId, animalCode, planId, userId);
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private <T> RunResult<T> runConcurrently(int threads, IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.apply(index);
                }));
            }
            start.countDown();
            List<T> successes = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    successes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
            return new RunResult<>(successes, failures);
        } finally {
            pool.shutdownNow();
        }
    }

    private record RunResult<T>(List<T> successes, List<Throwable> failures) {
    }

    private static final long ADMIN_ID = 9001L;
    private static final CurrentUser ADMIN = new CurrentUser(
            9001L, "admin", "管理员", "ANIMAL_ADMIN", Set.of("ANIMAL_ADMIN"), "后勤处", "jti-admin", null);
    private static final CurrentUser ADMIN_B = new CurrentUser(
            9002L, "admin2", "第二管理员", "ANIMAL_ADMIN", Set.of("ANIMAL_ADMIN"), "后勤处", "jti-admin2", null);
    private static final CurrentUser STUDENT = new CurrentUser(
            1L, "U2026001", "青檐一号", "STUDENT", Set.of("STUDENT"), "软件工程", "jti-student", null);
}
