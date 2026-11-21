package com.just.critter.integration;

import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.adopt.service.AuditedAdoption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
class AdoptionAuditIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdoptionAuditService service;

    @Test
    @DisplayName("审核通过：真实 MySQL 行锁读取并落待缴费、审核人、金额与说明")
    void approvesAndPersistsAuditResult() {
        long userId = insertUser("STU-ADOPT-");
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        long planId = insertPlan();
        String adoptionNo = "AD20261121901";
        insertPendingAdoption(adoptionNo, userId, animalId, planId);

        AuditedAdoption result = service.audit(adoptionNo,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, "真实库审核通过", 3200), 9001L);

        assertThat(result.status()).isEqualTo(AdoptionStatus.PENDING_PAYMENT);
        assertThat(result.payDeadline()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM adoption WHERE adoption_no = ?", String.class, adoptionNo))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT amount FROM adoption WHERE adoption_no = ?", Integer.class, adoptionNo))
                .isEqualTo(3200);
        assertThat(jdbc.queryForObject(
                "SELECT auditor_id FROM adoption WHERE adoption_no = ?", Long.class, adoptionNo))
                .isEqualTo(9001L);
        assertThat(jdbc.queryForObject(
                "SELECT audit_reason FROM adoption WHERE adoption_no = ?", String.class, adoptionNo))
                .isEqualTo("真实库审核通过");
    }

    @Test
    @DisplayName("审核驳回：真实 MySQL 落已驳回且不产生缴费期限")
    void rejectsAndPersistsReason() {
        long userId = insertUser("STU-REJECT-");
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        long planId = insertPlan();
        String adoptionNo = "AD20261121902";
        insertPendingAdoption(adoptionNo, userId, animalId, planId);

        AuditedAdoption result = service.audit(adoptionNo,
                new AdoptionAuditCommand(AdoptionDecision.REJECT, "材料不完整需补充", null), 9002L);

        assertThat(result.status()).isEqualTo(AdoptionStatus.REJECTED);
        assertThat(result.payDeadline()).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM adoption WHERE adoption_no = ?", String.class, adoptionNo))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject(
                "SELECT audit_reason FROM adoption WHERE adoption_no = ?", String.class, adoptionNo))
                .isEqualTo("材料不完整需补充");
    }

    private long insertUser(String prefix) {
        String suffix = shortSuffix();
        jdbc.update("""
                INSERT INTO sys_user (user_no, email, password_hash, nickname, primary_role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, prefix + suffix, prefix.toLowerCase() + suffix + "@test.local",
                "test-hash", "集成测试用户", "STUDENT", "ACTIVE");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPoint() {
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "审核集成测试点位-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(long pointId) {
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', '审核集成测试个体', ?, 'ON_CAMPUS')
                """, "CAT-IT-" + shortSuffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPlan() {
        jdbc.update("""
                INSERT INTO adoption_plan (plan_name, period, amount, benefits, enabled)
                VALUES (?, 'MONTH', 3000, '集成测试权益', 1)
                """, "集成测试方案-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }

    private void insertPendingAdoption(String adoptionNo, long userId, long animalId, long planId) {
        String animalCode = jdbc.queryForObject(
                "SELECT code FROM animal WHERE id = ?", String.class, animalId);
        jdbc.update("""
                INSERT INTO adoption (adoption_no, user_id, animal_id, animal_code, plan_id,
                                      amount, period, status, contact, promise_accepted_at, created_by)
                VALUES (?, ?, ?, ?, ?, 3000, ?, 'PENDING_AUDIT', '13800000000', ?, ?)
                """, adoptionNo, userId, animalId, animalCode, planId,
                AdoptionPeriod.MONTH.name(), LocalDateTime.of(2026, 11, 21, 10, 0), userId);
    }
}
