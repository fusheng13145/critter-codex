package com.just.critter.integration;

import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.service.fund.BatchConfirmResult;
import com.just.critter.service.fund.ConfirmDonationCommand;
import com.just.critter.service.fund.CreateDonationCommand;
import com.just.critter.service.fund.CreatedDonation;
import com.just.critter.service.fund.CreatedProject;
import com.just.critter.service.fund.DonationConfirmation;
import com.just.critter.service.fund.DonationService;
import com.just.critter.service.fund.ProjectAuditCommand;
import com.just.critter.service.fund.ProjectDetail;
import com.just.critter.service.fund.ProjectDraftCommand;
import com.just.critter.service.fund.ProjectService;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T3.1 真库验收：登记 ≠ 入账、核对后项目进度同步、金额不符转异常、批量 20 笔。
 * 使用独立 critter_codex_it（当前 Flyway V12）；前四个用例随测试事务回滚，
 * 批量用例单独提交，结束后按项目清理，避免残留演示数据。
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
class DonationIT extends MySqlIntegrationTestSupport {

    @Autowired
    private DonationService donationService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("登记：donation 落 PENDING_VERIFY，项目 raised_amount 与公开进度均为 0")
    void register_does_not_move_progress() {
        String projectNo = fundingProject("T3.1 登记两态");

        CreatedDonation created = donationService.create(donationCommand(projectNo, 20_000), student());

        assertThat(created.status()).isEqualTo(DonationStatus.PENDING_VERIFY);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, amount, verified_at, verifier_id, voucher_file_ids "
                        + "FROM donation WHERE donation_no = ?", created.donationNo());
        assertThat(row.get("status")).isEqualTo("PENDING_VERIFY");
        assertThat(((Number) row.get("amount")).intValue()).isEqualTo(20_000);
        assertThat(row.get("verified_at")).isNull();
        assertThat(row.get("verifier_id")).isNull();
        assertThat(String.valueOf(row.get("voucher_file_ids"))).contains("IT-VOUCHER");

        assertThat(raisedAmount(projectNo)).isZero();
        ProjectDetail detail = projectService.detail(projectNo, false);
        assertThat(detail.raisedAmount()).isZero();
        assertThat(detail.progressPercent()).isZero();
        assertThat(detail.donationCount()).isZero();
    }

    @Test
    @DisplayName("核对入账：donation -> VERIFIED，同事务更新 raised_amount、视图与公开进度")
    void confirm_updates_project_progress() {
        String projectNo = fundingProject("T3.1 入账联动");
        CreatedDonation created = donationService.create(donationCommand(projectNo, 20_000), student());

        DonationConfirmation confirmation = donationService.confirm(created.donationNo(),
                new ConfirmDonationCommand(20_000, LocalDateTime.parse("2026-11-20T10:00:00"),
                        List.of("IT-PROOF"), "财务到账"), admin());

        assertThat(confirmation.status()).isEqualTo(DonationStatus.VERIFIED);
        assertThat(confirmation.progress().raisedAmount()).isEqualTo(20_000);
        assertThat(confirmation.progress().progressPercent()).isEqualTo(6);

        Map<String, Object> donation = jdbc.queryForMap(
                "SELECT status, verifier_id, verified_at FROM donation WHERE donation_no = ?",
                created.donationNo());
        assertThat(donation.get("status")).isEqualTo("VERIFIED");
        assertThat(((Number) donation.get("verifier_id")).longValue()).isEqualTo(9001L);
        assertThat(donation.get("verified_at")).isNotNull();

        assertThat(raisedAmount(projectNo)).isEqualTo(20_000);
        assertThat(jdbc.queryForObject(
                "SELECT raised_amount FROM v_project_fund WHERE project_no = ?", Integer.class, projectNo))
                .isEqualTo(20_000);
        ProjectDetail detail = projectService.detail(projectNo, false);
        assertThat(detail.raisedAmount()).isEqualTo(20_000);
        assertThat(detail.progressPercent()).isEqualTo(6);
        assertThat(detail.donationCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = 1 "
                        + "AND type = 'DONATION_PROGRESS' AND biz_id = ?",
                Integer.class, created.donationNo())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM message WHERE receiver_id = 1 AND biz_id = ? ORDER BY id DESC LIMIT 1",
                String.class, created.donationNo())).contains("已入账");
    }

    @Test
    @DisplayName("金额不符：只标 ABNORMAL，不写 verified_at，不动项目进度")
    void mismatch_marks_abnormal_without_progress() {
        String projectNo = fundingProject("T3.1 金额异常");
        CreatedDonation created = donationService.create(donationCommand(projectNo, 20_000), student());

        DonationConfirmation confirmation = donationService.confirm(created.donationNo(),
                new ConfirmDonationCommand(15_000, LocalDateTime.parse("2026-11-20T10:00:00"),
                        null, "实际到账 150 元"), admin());

        assertThat(confirmation.status()).isEqualTo(DonationStatus.ABNORMAL);
        assertThat(confirmation.verifiedAt()).isNull();
        assertThat(confirmation.note()).contains("金额不一致");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, verified_at, verifier_id FROM donation WHERE donation_no = ?",
                created.donationNo());
        assertThat(row.get("status")).isEqualTo("ABNORMAL");
        assertThat(row.get("verified_at")).isNull();
        assertThat(((Number) row.get("verifier_id")).longValue()).isEqualTo(9001L);
        assertThat(raisedAmount(projectNo)).isZero();
        assertThat(projectService.detail(projectNo, false).progressPercent()).isZero();
    }

    @Test
    @DisplayName("核对：项目 raised_amount 被手工改动时返回 40106")
    void manual_raised_amount_is_rejected() {
        String projectNo = fundingProject("T3.1 进度防篡改");
        CreatedDonation created = donationService.create(donationCommand(projectNo, 20_000), student());
        jdbc.update("UPDATE project SET raised_amount = 30000 WHERE project_no = ?", projectNo);

        assertThatThrownBy(() -> donationService.confirm(created.donationNo(),
                new ConfirmDonationCommand(20_000, null, null, null), admin()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DONATION_NOT_VERIFIED.getCode()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("批量核对 20 笔：全部成功，项目进度一次同步到 20000 分")
    void batch_confirms_twenty_and_syncs_progress() {
        String projectNo = fundingProject("T3.1 批量二十笔");
        Long projectId = jdbc.queryForObject(
                "SELECT id FROM project WHERE project_no = ?", Long.class, projectNo);
        try {
            List<String> donationNos = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                CreatedDonation created = donationService.create(donationCommand(projectNo, 1_000), student());
                donationNos.add(created.donationNo());
            }

            BatchConfirmResult result = donationService.confirmBatch(donationNos, admin());

            assertThat(result.success()).isEqualTo(20);
            assertThat(result.failed()).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM donation WHERE project_id = ? AND status = 'VERIFIED'",
                    Integer.class, projectId)).isEqualTo(20);
            assertThat(raisedAmount(projectNo)).isEqualTo(20_000);
            assertThat(jdbc.queryForObject(
                    "SELECT raised_amount FROM v_project_fund WHERE project_no = ?", Integer.class, projectNo))
                    .isEqualTo(20_000);
            assertThat(projectService.detail(projectNo, false).donationCount()).isEqualTo(20);
        } finally {
            jdbc.update("DELETE FROM donation WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("批量核对：一笔成功一笔不存在，部分成功不回滚")
    void batch_partial_success() {
        String projectNo = fundingProject("T3.1 批量部分成功");
        Long projectId = jdbc.queryForObject(
                "SELECT id FROM project WHERE project_no = ?", Long.class, projectNo);
        try {
            CreatedDonation first = donationService.create(donationCommand(projectNo, 10_000), student());
            CreatedDonation second = donationService.create(donationCommand(projectNo, 20_000), student());

            BatchConfirmResult result = donationService.confirmBatch(
                    List.of(first.donationNo(), "DN209912319999"), admin());

            assertThat(result.success()).isEqualTo(1);
            assertThat(result.failed()).hasSize(1);
            assertThat(result.failed().getFirst().id()).isEqualTo("DN209912319999");
            assertThat(result.failed().getFirst().code()).isEqualTo(30410);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM donation WHERE donation_no = ?", String.class, first.donationNo()))
                    .isEqualTo("VERIFIED");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM donation WHERE donation_no = ?", String.class, second.donationNo()))
                    .isEqualTo("PENDING_VERIFY");
            assertThat(raisedAmount(projectNo)).isEqualTo(10_000);
        } finally {
            jdbc.update("DELETE FROM donation WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project_budget WHERE project_id = ?", projectId);
            jdbc.update("DELETE FROM project WHERE id = ?", projectId);
        }
    }

    private String fundingProject(String name) {
        CreatedProject created = projectService.create(new ProjectDraftCommand(
                name, "T3.1 真库验收项目，测试后回滚或清理。", "ANIMAL", 300_000,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"),
                List.of(new ProjectDraftCommand.BudgetItem("医疗", 300_000, null)),
                null, true), admin());
        projectService.audit(created.projectNo(), new ProjectAuditCommand("APPROVE", null), admin());
        return created.projectNo();
    }

    private CreateDonationCommand donationCommand(String projectNo, int amount) {
        return new CreateDonationCommand(projectNo, amount, false, "T3.1 真库测试", List.of("IT-VOUCHER"));
    }

    private int raisedAmount(String projectNo) {
        return jdbc.queryForObject(
                "SELECT raised_amount FROM project WHERE project_no = ?", Integer.class, projectNo);
    }

    private CurrentUser student() {
        return new CurrentUser(1L, "U2026001", "青檐一号", "STUDENT",
                Set.of("STUDENT"), "软件工程", "jti-student", null);
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti-admin", null);
    }
}
