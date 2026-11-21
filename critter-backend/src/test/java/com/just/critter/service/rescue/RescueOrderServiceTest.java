package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.rescue.OrderCodeGenerator;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2.2 验收：受理上报 -> 生成 PROCESSING 工单，工单号/时限/处置人/档案关联正确。
 */
@ExtendWith(MockitoExtension.class)
class RescueOrderServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private AnimalMapper animalMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private AuditLogService auditLogService;

    private RescueOrderService service;

    @BeforeEach
    void setUp() {
        service = new RescueOrderService(reportMapper, animalMapper, orderMapper,
                new OrderCodeGenerator((bizType, year) -> 7, CLOCK), auditLogService, CLOCK);
        lenient().when(orderMapper.update(any(Order.class), any())).thenReturn(1);
        lenient().when(reportMapper.update(any(Report.class), any())).thenReturn(1);
    }

    @Test
    @DisplayName("受理：生成 PROCESSING 工单，截止时间取上报创建后 24 小时")
    void accept_creates_processing_order() {
        Report report = pendingReport(5L);
        when(reportMapper.selectById(5L)).thenReturn(report);

        AcceptedReport accepted = service.acceptReport(5L,
                new AcceptReportCommand(3001L, null, "已联系处置人"), admin());

        assertThat(accepted.orderNo()).isEqualTo("RO20261121007");
        assertThat(accepted.orderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(accepted.reportStatus()).isEqualTo(ReportStatus.PROCESSING);
        assertThat(accepted.deadline()).isEqualTo(OffsetDateTime.parse("2026-11-22T10:31:00+08:00"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getOrderNo()).isEqualTo("RO20261121007");
        assertThat(saved.getReportId()).isEqualTo(5L);
        assertThat(saved.getType()).isEqualTo(ReportType.INJURED);
        assertThat(saved.getAcceptorId()).isEqualTo(9001L);
        assertThat(saved.getHandlerId()).isEqualTo(3001L);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(saved.getAcceptedAt()).isEqualTo(LocalDateTime.parse("2026-11-21T10:31:00"));
        assertThat(saved.getDeadlineAt()).isEqualTo(LocalDateTime.parse("2026-11-22T10:31:00"));
        assertThat(saved.getDisposeNote()).isEqualTo("已联系处置人");
        assertThat(saved.getCreatedBy()).isEqualTo(9001L);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PROCESSING);
        verify(reportMapper).update(any(Report.class), any());
        verify(auditLogService).success(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("受理：可选 animalCode 关联档案，未指定 handler 默认当前管理员")
    void accept_links_animal_and_defaults_handler() {
        Report report = pendingReport(5L);
        when(reportMapper.selectById(5L)).thenReturn(report);
        Animal animal = new Animal();
        animal.setId(42L);
        animal.setCode("CAT-2026-001");
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(animal);

        service.acceptReport(5L, new AcceptReportCommand(null, "CAT-2026-001", null), admin());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getAnimalId()).isEqualTo(42L);
        assertThat(captor.getValue().getHandlerId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("受理：上报创建时已有 PENDING 工单则复用，不重新发号")
    void accept_reuses_pending_order() {
        Report report = pendingReport(5L);
        when(reportMapper.selectById(5L)).thenReturn(report);
        Order pending = new Order();
        pending.setId(11L);
        pending.setOrderNo("RO20261121003");
        pending.setReportId(5L);
        pending.setType(ReportType.INJURED);
        pending.setStatus(OrderStatus.PENDING);
        pending.setDeadlineAt(LocalDateTime.parse("2026-11-22T10:31:00"));
        pending.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        when(orderMapper.selectOne(any())).thenReturn(pending);
        Animal animal = new Animal();
        animal.setId(42L);
        animal.setCode("CAT-2026-001");
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(animal);

        AcceptedReport accepted = service.acceptReport(5L,
                new AcceptReportCommand(null, "CAT-2026-001", null), admin());

        assertThat(accepted.orderNo()).isEqualTo("RO20261121003");
        assertThat(accepted.orderStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(pending.getAnimalId()).isEqualTo(42L);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderMapper, never()).insert(any(Order.class));
        verify(orderMapper).update(any(Order.class), any());
    }

    @Test
    @DisplayName("受理：非 PENDING 上报返回 30602，档案不存在返回 30410")
    void accept_rejects_non_pending_and_unknown_animal() {
        Report processing = pendingReport(5L);
        processing.setStatus(ReportStatus.PROCESSING);
        when(reportMapper.selectById(5L)).thenReturn(processing);
        assertCode(() -> service.acceptReport(5L, new AcceptReportCommand(null, null, null), admin()), 30602);

        Report pending = pendingReport(6L);
        when(reportMapper.selectById(6L)).thenReturn(pending);
        assertCode(() -> service.acceptReport(6L,
                new AcceptReportCommand(null, "CAT-2026-999", null), admin()), 30410);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    private Report pendingReport(Long id) {
        Report report = new Report();
        report.setId(id);
        report.setReportNo("RP20261121007");
        report.setType(ReportType.INJURED);
        report.setPointId(7L);
        report.setDescription("右后腿受伤无法正常站立");
        report.setReporterId(1001L);
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        return report;
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
