package com.just.critter.integration;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.AcceptedReport;
import com.just.critter.service.rescue.AcceptReportCommand;
import com.just.critter.service.rescue.ClosedReport;
import com.just.critter.service.rescue.CreateReportCommand;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.ReportFilter;
import com.just.critter.service.rescue.ReportService;
import com.just.critter.service.rescue.ReportSummary;
import com.just.critter.service.rescue.RescueOrderService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.CurrentUser;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.1 真库验收：report 落库字段、JSON 照片、24 小时时限、同点位重复提示。
 * 默认跳过；需要真库时设 CRITTER_MYSQL_IT=true 运行。
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
class ReportIT extends MySqlIntegrationTestSupport {

    @Autowired
    private ReportService reportService;

    @Autowired
    private RescueOrderService orderService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("上报落库：编号、PENDING、照片 JSON、联系方式、24 小时时限")
    void create_persists_report_and_deadline() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);

        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1", "F2"), "右后腿受伤无法正常站立", null, null, "13800000000"), 1001L);

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
    @DisplayName("列表：按状态/类型/点位过滤，返回点位名")
    void search_returns_created_report() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1"), "右后腿受伤无法正常站立", null, null, null), 1001L);

        PageResult<ReportSummary> page = reportService.search(
                new ReportFilter(List.of(ReportStatus.PENDING), ReportType.INJURED, pointId, null),
                new PageQuery(1, 10));

        assertThat(page.getList())
                .anySatisfy(summary -> {
                    assertThat(summary.reportNo()).isEqualTo(created.reportNo());
                    assertThat(summary.pointName()).isNotBlank();
                });
    }

    @Test
    @DisplayName("受理：上报转 PROCESSING，生成工单并沿用 24 小时时限")
    void accept_creates_order_and_moves_report() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1"), "右后腿受伤无法正常站立", null, null, null), 1001L);
        Long reportId = jdbc.queryForObject("SELECT id FROM report WHERE report_no = ?", Long.class,
                created.reportNo());
        LocalDateTime reportCreatedAt = jdbc.queryForObject(
                "SELECT created_at FROM report WHERE id = ?", LocalDateTime.class, reportId);

        AcceptedReport accepted = orderService.acceptReport(reportId,
                new AcceptReportCommand(3001L, null, "已联系处置人"), admin());

        assertThat(accepted.orderNo()).startsWith("RO2026");
        assertThat(accepted.reportStatus()).isEqualTo(ReportStatus.PROCESSING);
        assertThat(accepted.orderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(accepted.deadline().toLocalDateTime()).isEqualTo(reportCreatedAt.plusHours(24));

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT order_no, report_id, type, acceptor_id, handler_id, deadline_at, status, dispose_note "
                        + "FROM rescue_order WHERE order_no = ?", accepted.orderNo());
        assertThat(((Number) order.get("report_id")).longValue()).isEqualTo(reportId);
        assertThat(order.get("type")).isEqualTo("INJURED");
        assertThat(((Number) order.get("acceptor_id")).longValue()).isEqualTo(9001L);
        assertThat(((Number) order.get("handler_id")).longValue()).isEqualTo(3001L);
        assertThat(order.get("status")).isEqualTo("PROCESSING");
        assertThat(order.get("dispose_note")).isEqualTo("已联系处置人");
        assertThat(jdbc.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("关闭：无效上报写 close_reason 并转 CLOSED")
    void close_marks_report_closed() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1"), "右后腿受伤无法正常站立", null, null, null), 1001L);
        Long reportId = jdbc.queryForObject("SELECT id FROM report WHERE report_no = ?", Long.class,
                created.reportNo());

        ClosedReport closed = reportService.close(reportId, "重复上报");

        assertThat(closed.status()).isEqualTo(ReportStatus.CLOSED);
        assertThat(jdbc.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT close_reason FROM report WHERE id = ?", String.class, reportId))
                .isEqualTo("重复上报");
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }
}
