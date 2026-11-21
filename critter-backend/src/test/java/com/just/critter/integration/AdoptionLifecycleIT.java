package com.just.critter.integration;

import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import com.just.critter.adopt.domain.AdoptionPaymentCommand;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.adopt.service.AdoptionLifecycleService;
import com.just.critter.adopt.service.AdoptionPaymentResult;
import com.just.critter.adopt.service.AdoptionPaymentService;
import com.just.critter.adopt.service.AdoptionQueryService;
import com.just.critter.adopt.service.TerminatedAdoption;
import com.just.critter.support.api.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
class AdoptionLifecycleIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdoptionAuditService auditService;

    @Autowired
    private AdoptionPaymentService paymentService;

    @Autowired
    private AdoptionLifecycleService lifecycleService;

    @Autowired
    private AdoptionQueryService queryService;

    @Test
    @DisplayName("真实 MySQL：审核通过后缴费入账，缴费流水、认养单与动物标记同事务落库")
    void paymentActivatesAdoptionAndMarksAnimal() {
        Fixture fixture = fixture("AD20261121911");

        auditService.audit(fixture.adoptionNo,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, "真实库审核通过", null), 9001L);
        AdoptionPaymentResult result = paymentService.pay(fixture.adoptionNo,
                new AdoptionPaymentCommand(3000, OffsetDateTime.now(),
                        List.of("F20261121911"), "线下转账"), 9002L);

        assertThat(result.status()).isEqualTo(AdoptionStatus.ACTIVE);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM adoption WHERE adoption_no = ?", String.class, fixture.adoptionNo))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT certificate_no FROM adoption WHERE adoption_no = ?", String.class, fixture.adoptionNo))
                .startsWith("QYWL-AD-2026-");
        assertThat(jdbc.queryForObject(
                "SELECT pay_deadline FROM adoption WHERE adoption_no = ?",
                LocalDateTime.class, fixture.adoptionNo)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM adoption_payment WHERE adoption_id = ? AND verified_at IS NOT NULL",
                Integer.class, fixture.adoptionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM animal WHERE id = ?", String.class, fixture.animalId))
                .isEqualTo("ADOPTED");
        assertThat(jdbc.queryForObject(
                "SELECT adopted FROM animal WHERE id = ?", Integer.class, fixture.animalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = ? AND type = 'APPROVAL_RESULT'",
                Integer.class, fixture.userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = ? AND biz_id = ?",
                Integer.class, fixture.userId, fixture.adoptionNo)).isEqualTo(2);

        var detail = queryService.detail(fixture.adoptionNo, fixture.userId, false);
        assertThat(detail.status()).isEqualTo(AdoptionStatus.ACTIVE);
        assertThat(detail.payments()).hasSize(1);
        assertThat(detail.contactMasked()).isEqualTo("138****0000");
        assertThat(queryService.mine(fixture.userId, "ACTIVE", new PageQuery(1, 20)).getList())
                .extracting(item -> item.adoptionNo()).contains(fixture.adoptionNo);
        assertThat(queryService.list("ACTIVE", new PageQuery(1, 20)).getList())
                .extracting(item -> item.adoptionNo()).contains(fixture.adoptionNo);
    }

    @Test
    @DisplayName("真实 MySQL：终止生效关系后动物恢复在校园，证书仍可查询")
    void terminationReleasesAnimal() {
        Fixture fixture = fixture("AD20261121912");

        auditService.audit(fixture.adoptionNo,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, "真实库审核通过", null), 9001L);
        paymentService.pay(fixture.adoptionNo,
                new AdoptionPaymentCommand(3000, OffsetDateTime.now(),
                        List.of("F20261121912"), null), 9002L);

        TerminatedAdoption result = lifecycleService.terminate(fixture.adoptionNo, "认养人申请终止");

        assertThat(result.status()).isEqualTo(AdoptionStatus.TERMINATED);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM animal WHERE id = ?", String.class, fixture.animalId))
                .isEqualTo("ON_CAMPUS");
        assertThat(jdbc.queryForObject(
                "SELECT adopted FROM animal WHERE id = ?", Integer.class, fixture.animalId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = ? AND biz_id = ?",
                Integer.class, fixture.userId, fixture.adoptionNo)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM message WHERE receiver_id = ? AND biz_id = ? ORDER BY id DESC LIMIT 1",
                String.class, fixture.userId, fixture.adoptionNo)).contains("终止");
        assertThat(queryService.certificate(fixture.adoptionNo, fixture.userId, false).certificateNo())
                .startsWith("QYWL-AD-2026-");
    }

    private Fixture fixture(String adoptionNo) {
        long userId = insertUser();
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        long planId = insertPlan();
        insertPendingAdoption(adoptionNo, userId, animalId, planId);
        long adoptionId = jdbc.queryForObject(
                "SELECT id FROM adoption WHERE adoption_no = ?", Long.class, adoptionNo);
        return new Fixture(adoptionNo, adoptionId, userId, animalId);
    }

    private long insertUser() {
        String suffix = shortSuffix();
        jdbc.update("""
                INSERT INTO sys_user (user_no, email, password_hash, nickname, primary_role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "STU-LIFE-" + suffix, "life" + suffix + "@test.local",
                "test-hash", "青檐一号", "STUDENT", "ACTIVE");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPoint() {
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "生命周期集成测试点位-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(long pointId) {
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', '生命周期集成测试个体', ?, 'ON_CAMPUS')
                """, "CAT-IT-" + shortSuffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPlan() {
        jdbc.update("""
                INSERT INTO adoption_plan (plan_name, period, amount, benefits, enabled)
                VALUES (?, 'MONTH', 3000, '集成测试权益', 1)
                """, "生命周期方案-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertPendingAdoption(String adoptionNo, long userId, long animalId, long planId) {
        String animalCode = jdbc.queryForObject("SELECT code FROM animal WHERE id = ?", String.class, animalId);
        jdbc.update("""
                INSERT INTO adoption (adoption_no, user_id, animal_id, animal_code, plan_id,
                                      amount, period, status, contact, promise_accepted_at, created_by)
                VALUES (?, ?, ?, ?, ?, 3000, ?, 'PENDING_AUDIT', '13800000000', ?, ?)
                """, adoptionNo, userId, animalId, animalCode, planId,
                AdoptionPeriod.MONTH.name(), LocalDateTime.now(), userId);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }

    private record Fixture(String adoptionNo, long adoptionId, long userId, long animalId) {
    }
}
