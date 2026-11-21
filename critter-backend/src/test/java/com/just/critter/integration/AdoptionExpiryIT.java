package com.just.critter.integration;

import com.just.critter.adopt.service.AdoptionExpiryResult;
import com.just.critter.adopt.service.AdoptionExpiryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

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
class AdoptionExpiryIT extends MySqlIntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdoptionExpiryService expiryService;

    @Test
    @DisplayName("真实 MySQL：到期前七日提醒可重跑去重，过期转 EXPIRED 并释放动物")
    void remindsAndExpiresIdempotently() {
        LocalDate today = LocalDate.now(ZONE);
        long planId = insertPlan();
        long expiringAnimalId = insertAnimal("CAT-RMD");
        String expiringCode = jdbc.queryForObject(
                "SELECT code FROM animal WHERE id = ?", String.class, expiringAnimalId);
        String expiringNo = insertAdoption(expiringAnimalId, expiringCode, planId,
                today.minusMonths(1), today.plusDays(7), "ACTIVE");

        AdoptionExpiryResult first = expiryService.process(today);
        assertThat(first.reminded()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE type = 'ADOPTION_EXPIRY' AND biz_id = ?",
                Integer.class, expiringNo)).isEqualTo(1);

        AdoptionExpiryResult rerun = expiryService.process(today);
        assertThat(rerun.reminded()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE type = 'ADOPTION_EXPIRY' AND biz_id = ?",
                Integer.class, expiringNo)).isEqualTo(1);

        long overdueAnimalId = insertAnimal("CAT-OVD");
        String overdueCode = jdbc.queryForObject(
                "SELECT code FROM animal WHERE id = ?", String.class, overdueAnimalId);
        String overdueNo = insertAdoption(overdueAnimalId, overdueCode, planId,
                today.minusMonths(2), today.minusDays(1), "ACTIVE");

        AdoptionExpiryResult expired = expiryService.process(today);
        assertThat(expired.expired()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM adoption WHERE adoption_no = ?", String.class, overdueNo))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM animal WHERE id = ?", String.class, overdueAnimalId))
                .isEqualTo("ON_CAMPUS");
        assertThat(jdbc.queryForObject(
                "SELECT adopted FROM animal WHERE id = ?", Integer.class, overdueAnimalId)).isZero();
    }

    private long insertPlan() {
        jdbc.update("INSERT INTO adoption_plan (plan_name, period, amount, enabled) "
                + "VALUES (?, 'MONTH', 10000, 1)", "到期任务测试方案-" + suffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(String code) {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id,
                                    status, adopted, status_changed_at)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', '到期任务集成测试个体', ?, 'ADOPTED', 1, NOW())
                """, code + "-" + suffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String insertAdoption(long animalId, String animalCode, long planId,
                                  LocalDate start, LocalDate end, String status) {
        String adoptionNo = "ADIT" + suffix();
        jdbc.update("""
                INSERT INTO adoption (adoption_no, user_id, animal_id, animal_code, plan_id,
                                      amount, period, start_date, end_date, status,
                                      contact, promise_accepted_at)
                VALUES (?, 1, ?, ?, ?, 10000, 'MONTH', ?, ?, ?, '13800000000', NOW())
                """, adoptionNo, animalId, animalCode, planId, start, end, status);
        return adoptionNo;
    }

    private String suffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }
}
