package com.just.critter.service.rescue;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.model.SysLog;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2.3 验收：工单列表/详情时间线/状态流转；TIMEOUT 只留给定时任务，不开放手工流转。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private BasePointMapper pointMapper;
    @Mock
    private AnimalMapper animalMapper;
    @Mock
    private SysLogMapper sysLogMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MessageRepository messages;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderMapper, reportMapper, pointMapper, animalMapper,
                sysLogMapper, auditLogService, messages, new ObjectMapper(), CLOCK);
        lenient().when(messages.insert(any(MessageWrite.class))).thenReturn(1);
    }

    @Test
    @DisplayName("列表：返回工单号、来源上报、点位与超时标记")
    void search_returns_order_summary() {
        Order order = order(5L, OrderStatus.PROCESSING,
                LocalDateTime.parse("2026-11-22T10:31:00"));
        Page<Order> page = new Page<>(1, 10);
        page.setRecords(List.of(order));
        page.setTotal(1);
        when(orderMapper.selectPage(any(), any())).thenReturn(page);
        when(reportMapper.selectBatchIds(any())).thenReturn(List.of(report()));
        when(pointMapper.selectBatchIds(any())).thenReturn(List.of(point()));

        PageResult<OrderSummary> result = service.search(
                new OrderFilter(List.of(OrderStatus.PROCESSING), ReportType.INJURED, null),
                new PageQuery(1, 10));

        assertThat(result.getPage().getTotal()).isEqualTo(1);
        assertThat(result.getList()).singleElement().satisfies(summary -> {
            assertThat(summary.orderNo()).isEqualTo("RO20261121001");
            assertThat(summary.reportNo()).isEqualTo("RP20261121007");
            assertThat(summary.pointName()).isEqualTo("东区宿舍七号楼");
            assertThat(summary.status()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(summary.overdue()).isFalse();
        });
    }

    @Test
    @DisplayName("详情：返回来源上报、允许目标（不含 TIMEOUT）与审计时间线")
    void detail_returns_report_and_timeline() {
        when(orderMapper.selectOne(any())).thenReturn(order(5L, OrderStatus.PROCESSING,
                LocalDateTime.parse("2026-11-22T10:31:00")));
        when(reportMapper.selectById(5L)).thenReturn(report());
        when(animalMapper.selectById(42L)).thenReturn(animal());
        when(sysLogMapper.selectList(any())).thenReturn(List.of(
                log("REPORT_ACCEPT", "{\"status\":\"PENDING\"}", "{\"status\":\"PROCESSING\"}"),
                log("ORDER_DISPOSE", "{\"status\":\"PROCESSING\"}", "{\"status\":\"TO_REVISIT\"}")));

        OrderDetail detail = service.detail("RO20261121001");

        assertThat(detail.orderNo()).isEqualTo("RO20261121001");
        assertThat(detail.reportNo()).isEqualTo("RP20261121007");
        assertThat(detail.animalCode()).isEqualTo("CAT-2026-001");
        assertThat(detail.allowed()).containsExactly(OrderStatus.TO_REVISIT, OrderStatus.CLOSED);
        assertThat(detail.timeline()).hasSize(2);
        assertThat(detail.timeline().get(0).actionLabel()).isEqualTo("受理并转工单");
        assertThat(detail.timeline().get(1).actionLabel()).isEqualTo("完成处置，转待回访");
        assertThat(detail.timeline().get(1).toStatus()).isEqualTo(OrderStatus.TO_REVISIT);
    }

    @Test
    @DisplayName("处置完成：PROCESSING -> TO_REVISIT，写处置说明与审计")
    void dispose_done_moves_to_revisit() {
        Order order = order(5L, OrderStatus.PROCESSING, LocalDateTime.parse("2026-11-22T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderTransitionResult result = service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.TO_REVISIT, " 已送医观察 ", null, null), admin());

        assertThat(result.status()).isEqualTo(OrderStatus.TO_REVISIT);
        assertThat(result.disposeNote()).isEqualTo("已送医观察");
        assertThat(result.allowed()).containsExactly(OrderStatus.CLOSED, OrderStatus.PROCESSING);
        assertThat(order.getDisposeNote()).isEqualTo("已送医观察");
        verify(orderMapper).updateById(order);
        verify(auditLogService).successAs(anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), any());
    }

    @Test
    @DisplayName("流转成功后向上报人写 ORDER_PROGRESS")
    void transition_writes_order_progress_message() {
        Order order = order(5L, OrderStatus.PROCESSING, LocalDateTime.parse("2026-11-22T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(reportMapper.selectById(5L)).thenReturn(report());

        service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.TO_REVISIT, "已送医观察", null, null), admin());

        ArgumentCaptor<MessageWrite> captor = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(captor.capture());
        MessageWrite message = captor.getValue();
        assertThat(message.receiverId()).isEqualTo(1001L);
        assertThat(message.type()).isEqualTo("ORDER_PROGRESS");
        assertThat(message.bizType()).isEqualTo("RESCUE_ORDER");
        assertThat(message.bizId()).isEqualTo("RO20261121001");
        assertThat(message.title()).contains("待回访");
    }

    @Test
    @DisplayName("无法处置：PROCESSING -> CLOSED 必须填原因，空原因 30401")
    void cannot_dispose_requires_reason() {
        Order order = order(5L, OrderStatus.PROCESSING, LocalDateTime.parse("2026-11-22T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertCode(() -> service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.CLOSED, null, "  ", null), admin()), 30401);

        OrderTransitionResult result = service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.CLOSED, null, "动物死亡", null), admin());
        assertThat(result.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order.getCloseReason()).isEqualTo("动物死亡");
        assertThat(result.allowed()).isEmpty();
    }

    @Test
    @DisplayName("回访正常：TO_REVISIT -> CLOSED，未填原因时给默认结案说明")
    void revisit_ok_closes_order() {
        Order order = order(5L, OrderStatus.TO_REVISIT, LocalDateTime.parse("2026-11-22T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderTransitionResult result = service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.CLOSED, null, null, null), admin());

        assertThat(result.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order.getCloseReason()).isEqualTo("回访确认恢复良好");
        assertThat(result.closeReason()).isEqualTo("回访确认恢复良好");
    }

    @Test
    @DisplayName("回访异常：TO_REVISIT -> PROCESSING 必须填异常说明")
    void revisit_anomaly_requires_note() {
        Order order = order(5L, OrderStatus.TO_REVISIT, LocalDateTime.parse("2026-11-22T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertCode(() -> service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.PROCESSING, null, null, null), admin()), 30301);

        OrderTransitionResult result = service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.PROCESSING, "伤口感染", null, null), admin());
        assertThat(result.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getDisposeNote()).isEqualTo("伤口感染");
    }

    @Test
    @DisplayName("非法流转：TIMEOUT 不允许手工触发，已关闭工单无目标，未知工单 30410")
    void invalid_transitions_are_rejected() {
        Order pending = order(5L, OrderStatus.PENDING, LocalDateTime.parse("2026-11-21T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(pending);
        assertCode(() -> service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.TIMEOUT, null, null, null), admin()), 30602);

        Order closed = order(6L, OrderStatus.CLOSED, LocalDateTime.parse("2026-11-21T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(closed);
        assertThatThrownBy(() -> service.transition("RO20261121001",
                new OrderStatusCommand(OrderStatus.PROCESSING, null, null, null), admin()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(30602);
                    assertThat((Map<String, Object>) ex.getData()).containsEntry("allowed", List.of());
                });

        when(orderMapper.selectOne(any())).thenReturn(null);
        assertCode(() -> service.transition("RO20261121999",
                new OrderStatusCommand(OrderStatus.PROCESSING, null, null, null), admin()), 30410);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    private Order order(Long reportId, OrderStatus status, LocalDateTime deadlineAt) {
        Order order = new Order();
        order.setOrderNo("RO20261121001");
        order.setReportId(reportId);
        order.setAnimalId(42L);
        order.setType(ReportType.INJURED);
        order.setAcceptorId(9001L);
        order.setHandlerId(3001L);
        order.setDeadlineAt(deadlineAt);
        order.setAcceptedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        order.setUpdatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        return order;
    }

    private Report report() {
        Report report = new Report();
        report.setId(5L);
        report.setReportNo("RP20261121007");
        report.setType(ReportType.INJURED);
        report.setPointId(7L);
        report.setDescription("右后腿受伤无法正常站立");
        report.setFileIds(List.of("F1"));
        report.setReporterId(1001L);
        report.setAnimalId(42L);
        report.setStatus(ReportStatus.PROCESSING);
        return report;
    }

    private BasePoint point() {
        BasePoint point = new BasePoint();
        point.setId(7L);
        point.setPointName("东区宿舍七号楼");
        return point;
    }

    private Animal animal() {
        Animal animal = new Animal();
        animal.setId(42L);
        animal.setCode("CAT-2026-001");
        return animal;
    }

    private SysLog log(String action, String before, String after) {
        SysLog log = new SysLog();
        log.setAction(action);
        log.setActorId(9001L);
        log.setActorRole("ANIMAL_ADMIN");
        log.setResult("SUCCESS");
        log.setBeforeJson(before);
        log.setAfterJson(after);
        log.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        return log;
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
    }

    private void assertCode(ThrowingRunnable action, int expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
