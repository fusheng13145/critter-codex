package com.just.critter.service.rescue;

import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.OrderEvent;
import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.rescue.OrderCodeGenerator;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * 受理上报并生成救助工单。工单初始状态由 StateMachines.order() 表驱动：
 * PENDING -> ACCEPT -> PROCESSING；受理后 24 小时时限沿用上报创建时间。
 */
@Service
public class RescueOrderService {

    private static final int DEDUP_HOURS = 24;

    private final ReportMapper reports;
    private final AnimalMapper animals;
    private final OrderMapper orders;
    private final OrderCodeGenerator codeGenerator;
    private final AuditLogService auditLog;
    private final Clock clock;

    public RescueOrderService(ReportMapper reports,
                              AnimalMapper animals,
                              OrderMapper orders,
                              OrderCodeGenerator codeGenerator,
                              AuditLogService auditLog,
                              Clock clock) {
        this.reports = reports;
        this.animals = animals;
        this.orders = orders;
        this.codeGenerator = codeGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public AcceptedReport acceptReport(Long reportId, AcceptReportCommand command, CurrentUser actor) {
        Report report = reportId == null ? null : reports.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "上报不存在");
        }
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "仅待受理上报可受理");
        }
        Long animalId = resolveAnimalId(command == null ? null : command.animalCode(), report.getAnimalId());
        Long handlerId = command == null || command.handlerId() == null ? actor.id() : command.handlerId();
        String note = command == null ? null : trimToNull(command.note());

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        LocalDateTime deadlineAt = report.getCreatedAt() == null
                ? now.plusHours(DEDUP_HOURS) : report.getCreatedAt().plusHours(DEDUP_HOURS);
        OrderStatus target = StateMachines.order().fire(OrderStatus.PENDING, OrderEvent.ACCEPT,
                TransitionContext.of(roleOf(actor)));
        if (target != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "工单受理状态非法");
        }

        Order order = new Order();
        order.setOrderNo(codeGenerator.next());
        order.setReportId(report.getId());
        order.setAnimalId(animalId);
        order.setType(report.getType());
        order.setAcceptorId(actor.id());
        order.setHandlerId(handlerId);
        order.setDeadlineAt(deadlineAt);
        order.setAcceptedAt(now);
        order.setStatus(target);
        order.setDisposeNote(note);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setCreatedBy(actor.id());
        orders.insert(order);

        report.setStatus(ReportStatus.PROCESSING);
        report.setUpdatedAt(now);
        reports.updateById(report);

        auditLog.success("REPORT_ACCEPT", "RESCUE_ORDER", order.getOrderNo(),
                Map.of("reportNo", report.getReportNo(), "status", ReportStatus.PENDING.name()),
                Map.of("orderNo", order.getOrderNo(), "status", ReportStatus.PROCESSING.name(),
                        "handlerId", String.valueOf(handlerId)));
        return new AcceptedReport(order.getOrderNo(), report.getReportNo(), report.getStatus(),
                order.getStatus(), toOffset(deadlineAt));
    }

    private Long resolveAnimalId(String rawCode, Long reportAnimalId) {
        String code = trimToNull(rawCode);
        if (code == null) {
            return reportAnimalId;
        }
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "档案不存在：" + code);
        }
        return animal.getId();
    }

    private Role roleOf(CurrentUser actor) {
        if (actor == null || actor.primaryRole() == null) {
            return Role.STUDENT;
        }
        try {
            return Role.valueOf(actor.primaryRole().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Role.STUDENT;
        }
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
