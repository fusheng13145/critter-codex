package com.just.critter.integration;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.AcceptedReport;
import com.just.critter.service.rescue.AcceptReportCommand;
import com.just.critter.service.rescue.CreateReportCommand;
import com.just.critter.service.rescue.CreatedReport;
import com.just.critter.service.rescue.OrderDetail;
import com.just.critter.service.rescue.OrderFilter;
import com.just.critter.service.rescue.OrderService;
import com.just.critter.service.rescue.OrderStatusCommand;
import com.just.critter.service.rescue.OrderSummary;
import com.just.critter.service.rescue.OrderTransitionResult;
import com.just.critter.service.rescue.ReportService;
import com.just.critter.service.rescue.RescueOrderService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.3 真库验收：工单列表/详情时间线、PROCESSING -> TO_REVISIT -> CLOSED 全链路落库。
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
class OrderIT extends MySqlIntegrationTestSupport {

    @Autowired
    private ReportService reportService;

    @Autowired
    private RescueOrderService rescueOrderService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void auth() {
        AuthContext.set(admin());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("列表与详情：工单可按状态查到，详情带来源上报与受理时间线")
    void search_and_detail_include_timeline() {
        AcceptedReport accepted = createAndAccept();

        PageResult<OrderSummary> page = orderService.search(
                new OrderFilter(List.of(OrderStatus.PROCESSING), ReportType.INJURED, null),
                new PageQuery(1, 10));

        assertThat(page.getList()).anySatisfy(summary -> {
            assertThat(summary.orderNo()).isEqualTo(accepted.orderNo());
            assertThat(summary.reportNo()).isEqualTo(accepted.reportNo());
            assertThat(summary.status()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(summary.pointName()).isNotBlank();
        });

        OrderDetail detail = orderService.detail(accepted.orderNo());
        assertThat(detail.reportNo()).isEqualTo(accepted.reportNo());
        assertThat(detail.handlerId()).isEqualTo(3001L);
        assertThat(detail.allowed()).containsExactly(OrderStatus.TO_REVISIT, OrderStatus.CLOSED);
        assertThat(detail.timeline()).anySatisfy(item -> {
            assertThat(item.action()).isEqualTo("REPORT_ACCEPT");
            assertThat(item.fromStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(item.toStatus()).isEqualTo(OrderStatus.PROCESSING);
        });
    }

    @Test
    @DisplayName("流转：处置完成转待回访，回访正常后关闭；状态、说明与时间线均落库")
    void transition_processing_to_revisit_then_closed() {
        AcceptedReport accepted = createAndAccept();

        OrderTransitionResult revisit = orderService.transition(accepted.orderNo(),
                new OrderStatusCommand(OrderStatus.TO_REVISIT, "已送医观察", null, null), admin());
        assertThat(revisit.status()).isEqualTo(OrderStatus.TO_REVISIT);
        assertThat(revisit.allowed()).containsExactly(OrderStatus.CLOSED, OrderStatus.PROCESSING);
        assertThat(jdbc.queryForObject("SELECT status FROM rescue_order WHERE order_no = ?",
                String.class, accepted.orderNo())).isEqualTo("TO_REVISIT");
        assertThat(jdbc.queryForObject("SELECT dispose_note FROM rescue_order WHERE order_no = ?",
                String.class, accepted.orderNo())).isEqualTo("已送医观察");

        OrderTransitionResult closed = orderService.transition(accepted.orderNo(),
                new OrderStatusCommand(OrderStatus.CLOSED, null, "回访恢复良好", null), admin());
        assertThat(closed.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(closed.allowed()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT close_reason FROM rescue_order WHERE order_no = ?",
                String.class, accepted.orderNo())).isEqualTo("回访恢复良好");

        OrderDetail detail = orderService.detail(accepted.orderNo());
        assertThat(detail.timeline()).anySatisfy(item -> {
            assertThat(item.action()).isEqualTo("ORDER_DISPOSE");
            assertThat(item.toStatus()).isEqualTo(OrderStatus.TO_REVISIT);
        });
        assertThat(detail.timeline()).anySatisfy(item -> {
            assertThat(item.action()).isEqualTo("ORDER_REVISIT_OK");
            assertThat(item.toStatus()).isEqualTo(OrderStatus.CLOSED);
        });
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = 1 "
                        + "AND type = 'ORDER_PROGRESS' AND biz_id = ?",
                Integer.class, accepted.orderNo())).isEqualTo(2);
    }

    private AcceptedReport createAndAccept() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        CreatedReport created = reportService.create(new CreateReportCommand(
                "INJURED", pointId, List.of("F1"), "右后腿受伤无法正常站立", null, null, null), 1L);
        Long reportId = jdbc.queryForObject("SELECT id FROM report WHERE report_no = ?", Long.class,
                created.reportNo());
        return rescueOrderService.acceptReport(reportId,
                new AcceptReportCommand(3001L, null, "已联系处置人"), admin());
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }
}
