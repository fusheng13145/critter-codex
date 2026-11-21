package com.just.critter.integration;

import com.just.critter.service.rescue.CreateReportCommand;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.1 真库验收：report 落库字段、JSON 照片、24 小时时限、同点位重复提示。
 * 默认跳过；需要真库时设 CRITTER_MYSQL_IT=true 运行。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/critter_codex"
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=1234",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "CRITTER_MYSQL_IT", matches = "true")
@Transactional
class ReportIT {

    @Autowired
    private ReportService reportService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("上报落库：编号、PENDING、照片 JSON、联系方式、24 小时时限")
    void create_persists_report_and_deadline() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);

        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1", "F2"), "右后腿受伤无法正常站立", null, "13800000000"), 1001L);

        assertThat(created.reportNo()).startsWith("RP2026");
        assertThat(created.status().name()).isEqualTo("PENDING");
        assertThat(created.duplicated()).isFalse();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT report_no, type, point_id, description, file_ids, reporter_id, status, contact, created_at "
                        + "FROM report WHERE report_no = ?", created.reportNo());
        assertThat(row.get("type")).isEqualTo("INJURED");
        assertThat(((Number) row.get("point_id")).longValue()).isEqualTo(pointId);
        assertThat(row.get("description")).isEqualTo("右后腿受伤无法正常站立");
        assertThat(String.valueOf(row.get("file_ids"))).contains("F1", "F2");
        assertThat(((Number) row.get("reporter_id")).longValue()).isEqualTo(1001L);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("contact")).isEqualTo("13800000000");

        LocalDateTime createdAt = (LocalDateTime) row.get("created_at");
        assertThat(created.deadline().toLocalDateTime()).isEqualTo(createdAt.plusHours(24));
    }

    @Test
    @DisplayName("同点位 24 小时内重复提交：新单落库并返回原单号")
    void duplicate_report_is_flagged_and_both_persisted() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        CreateReportCommand command = new CreateReportCommand(
                "INJURED", pointId, List.of("F1"), "右后腿受伤无法正常站立", null, null);

        CreatedReport first = reportService.create(command, 1001L);
        CreatedReport second = reportService.create(command, 1002L);

        assertThat(second.duplicated()).isTrue();
        assertThat(second.duplicateOf()).isEqualTo(first.reportNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE point_id = ?", Integer.class, pointId))
                .isEqualTo(2);
    }
}
