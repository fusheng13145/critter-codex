package com.just.critter.integration;

import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.care.service.VisitCommand;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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
class MedicalIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VisitService visitService;

    @Autowired
    private MedicalQueryService queryService;

    @Test
    @DisplayName("真实 MySQL：无工单就诊写入来源备注、费用与 JSON 影像，并可按编号查询")
    void registersAndQueriesVisit() {
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        long clinicId = jdbc.queryForObject(
                "SELECT id FROM base_clinic ORDER BY id LIMIT 1", Long.class);

        VisitRegistration result = visitService.register(new VisitCommand(
                animalId, null, clinicId, OffsetDateTime.now().minusHours(1),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261121001", "F20261121002"), "校园巡查发现"), 9001L);

        assertThat(result.visitNo()).startsWith("VS");
        assertThat(jdbc.queryForObject(
                "SELECT source_note FROM visit WHERE visit_no = ?", String.class, result.visitNo()))
                .isEqualTo("校园巡查发现");
        assertThat(jdbc.queryForObject(
                "SELECT cost FROM visit WHERE visit_no = ?", Integer.class, result.visitNo()))
                .isEqualTo(12000);
        assertThat(jdbc.queryForObject(
                "SELECT JSON_LENGTH(file_ids) FROM visit WHERE visit_no = ?",
                Integer.class, result.visitNo())).isEqualTo(2);

        var detail = queryService.visit(result.visitNo());
        assertThat(detail.animalId()).isEqualTo(animalId);
        assertThat(detail.animalCode()).startsWith("CAT-IT-");
        assertThat(detail.clinicId()).isEqualTo(clinicId);
        assertThat(detail.diagnosis()).isEqualTo("软组织挫伤");
        assertThat(detail.treatment()).isEqualTo("清创包扎");
        assertThat(detail.fileIds()).containsExactly("F20261121001", "F20261121002");
        assertThat(detail.orderId()).isNull();

        assertThat(queryService.clinics()).isNotEmpty();
        assertThat(queryService.clinics().get(0).priceNote()).isNotBlank();
    }

    private long insertPoint() {
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "医疗集成测试点位-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(long pointId) {
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', '医疗集成测试个体', ?, 'ON_CAMPUS')
                """, "CAT-IT-" + shortSuffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }
}
