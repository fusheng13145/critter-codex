package com.just.critter.integration;

import com.just.critter.care.service.ClinicCommand;
import com.just.critter.care.service.ClinicService;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.care.service.VisitCommand;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import com.just.critter.care.service.VisitUpdateCommand;
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
    private ClinicService clinicService;

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

        var visits = queryService.visitsByAnimal(detail.animalCode());
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).visitNo()).isEqualTo(result.visitNo());
        assertThat(visits.get(0).diagnosis()).isEqualTo("软组织挫伤");
        assertThat(visits.get(0).clinicName()).isNotBlank();

        assertThat(queryService.clinics()).isNotEmpty();
        assertThat(queryService.clinics().get(0).priceNote()).isNotBlank();
    }

    @Test
    @DisplayName("真实 MySQL：新增医院落库，就诊纠错改字段并写审计日志")
    void createsClinicAndCorrectsVisit() {
        ClinicView clinic = clinicService.create(new ClinicCommand(
                "集成测试合作医院（示例）", "李医生", "0000-0000002",
                "东门路 2 号", "绝育 280 元（示例价）"), 9001L);

        assertThat(clinic.clinicId()).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT clinic_name FROM base_clinic WHERE id = ?", String.class, clinic.clinicId()))
                .isEqualTo("集成测试合作医院（示例）");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_log WHERE action = 'CLINIC_CREATE' AND biz_id = ?",
                Integer.class, String.valueOf(clinic.clinicId()))).isEqualTo(1);

        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        VisitRegistration registered = visitService.register(new VisitCommand(
                animalId, null, clinic.clinicId(), OffsetDateTime.now().minusHours(2),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261121001"), "校园巡查发现"), 9001L);

        var updated = visitService.update(registered.visitNo(), new VisitUpdateCommand(
                "左前肢跛行", "软组织挫伤（复核）", "清创包扎、限制活动", 9000,
                List.of("F20261121001", "F20261121002"), "费用按实际票据更正"), 9001L);

        assertThat(updated.diagnosis()).isEqualTo("软组织挫伤（复核）");
        assertThat(updated.cost()).isEqualTo(9000);
        assertThat(updated.fileIds()).containsExactly("F20261121001", "F20261121002");
        assertThat(jdbc.queryForObject(
                "SELECT cost FROM visit WHERE visit_no = ?", Integer.class, registered.visitNo()))
                .isEqualTo(9000);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_log WHERE action = 'VISIT_CORRECT' AND biz_id = ?",
                Integer.class, registered.visitNo())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT before_json FROM sys_log
                WHERE action = 'VISIT_CORRECT' AND biz_id = ?
                """, String.class, registered.visitNo())).contains("软组织挫伤");
        assertThat(jdbc.queryForObject("""
                SELECT after_json FROM sys_log
                WHERE action = 'VISIT_CORRECT' AND biz_id = ?
                """, String.class, registered.visitNo())).contains("费用按实际票据更正");
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
