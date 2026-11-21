package com.just.critter.service.rescue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.OrderEvent;
import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.model.SysLog;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.StateTransitionException;
import com.just.critter.domain.statemachine.Transition;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单看板与流转：列表/详情读模型 + 表驱动状态流转。
 *
 * <p>工单在受理时才创建，因此正常数据从 PROCESSING 起步；PENDING/TIMEOUT 留给后续
 * 每小时扫描任务。TIMEOUT 只能由系统任务触发，不开放手工流转。</p>
 */
@Service
public class OrderService {

    private static final String BIZ_TYPE = "RESCUE_ORDER";
    private static final int MAX_CLOSE_REASON = 255;

    private final OrderMapper orders;
    private final ReportMapper reports;
    private final BasePointMapper points;
    private final AnimalMapper animals;
    private final SysLogMapper sysLogs;
    private final AuditLogService auditLog;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderService(OrderMapper orders,
                        ReportMapper reports,
                        BasePointMapper points,
                        AnimalMapper animals,
                        SysLogMapper sysLogs,
                        AuditLogService auditLog,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.orders = orders;
        this.reports = reports;
        this.points = points;
        this.animals = animals;
        this.sysLogs = sysLogs;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PageResult<OrderSummary> search(OrderFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Order> page = new Page<>(safe.getPage(), safe.getSize());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                query.in(Order::getStatus, filter.statuses());
            }
            if (filter.type() != null) {
                query.eq(Order::getType, filter.type());
            }
            if (filter.overdue() != null) {
                if (filter.overdue()) {
                    query.and(w -> w.eq(Order::getStatus, OrderStatus.TIMEOUT)
                            .or(x -> x.eq(Order::getStatus, OrderStatus.PENDING)
                                    .lt(Order::getDeadlineAt, now)));
                } else {
                    query.ne(Order::getStatus, OrderStatus.TIMEOUT)
                            .and(w -> w.ne(Order::getStatus, OrderStatus.PENDING)
                                    .or(x -> x.ge(Order::getDeadlineAt, now)));
                }
            }
        }
        query.orderByDesc(Order::getCreatedAt).orderByDesc(Order::getId);

        IPage<Order> result = orders.selectPage(page, query);
        List<Order> records = result.getRecords();
        Set<Long> reportIds = records.stream().map(Order::getReportId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Report> reportList = reportIds.isEmpty() ? List.of() : reports.selectBatchIds(reportIds);
        Map<Long, Report> reportMap = reportList.stream()
                .collect(Collectors.toMap(Report::getId, report -> report, (a, b) -> a));
        Set<Long> pointIds = reportList.stream().map(Report::getPointId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<BasePoint> pointList = pointIds.isEmpty() ? List.of() : points.selectBatchIds(pointIds);
        Map<Long, BasePoint> pointMap = pointList.stream()
                .collect(Collectors.toMap(BasePoint::getId, point -> point, (a, b) -> a));

        List<OrderSummary> list = records.stream()
                .map(order -> toSummary(order, reportMap.get(order.getReportId()), pointMap, now))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    public OrderDetail detail(String orderNo) {
        Order order = requireOrder(orderNo);
        Report report = order.getReportId() == null ? null : reports.selectById(order.getReportId());
        BasePoint point = report == null || report.getPointId() == null
                ? null : points.selectById(report.getPointId());
        Animal animal = order.getAnimalId() == null ? null : animals.selectById(order.getAnimalId());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        return new OrderDetail(
                order.getOrderNo(),
                order.getReportId(),
                report == null ? null : report.getReportNo(),
                order.getType(),
                report == null ? null : report.getPointId(),
                point == null ? null : point.getPointName(),
                report == null ? null : report.getDescription(),
                report == null || report.getFileIds() == null ? List.of() : List.copyOf(report.getFileIds()),
                order.getAnimalId(),
                animal == null ? null : animal.getCode(),
                order.getStatus(),
                overdue(order, now),
                order.getCreatedAt(),
                toOffset(order.getDeadlineAt()),
                order.getAcceptedAt(),
                order.getAcceptorId(),
                order.getHandlerId(),
                order.getDisposeNote(),
                order.getCloseReason(),
                allowedTargets(order.getStatus()),
                timeline(order.getOrderNo()));
    }

    @Transactional
    public OrderTransitionResult transition(String orderNo, OrderStatusCommand command, CurrentUser actor) {
        if (command == null || command.targetStatus() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请指定目标状态");
        }
        Order order = requireOrder(orderNo);
        OrderStatus target = command.targetStatus();
        if (target == OrderStatus.TIMEOUT) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "超时由系统任务标记，不支持手工流转");
        }

        OrderEvent event = eventFor(order.getStatus(), target);
        if (event == null) {
            throw illegal(order.getStatus(), target);
        }
        String note = trimToNull(command.note());
        String reason = trimToNull(command.reason());
        if (reason != null && reason.length() > MAX_CLOSE_REASON) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "关闭原因最多 255 个字符");
        }
        if (event == OrderEvent.DISPOSE_DONE && note == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写处置说明");
        }
        if (event == OrderEvent.CANNOT_DISPOSE && reason == null) {
            throw new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED, "请填写关闭原因");
        }
        if (event == OrderEvent.REVISIT_ANOMALY && note == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写回访异常说明");
        }

        OrderStatus before = order.getStatus();
        OrderStatus next = StateMachines.order().fire(before, event, TransitionContext.of(roleOf(actor)));
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        if (event == OrderEvent.ACCEPT) {
            order.setAcceptorId(actor == null ? null : actor.id());
            order.setAcceptedAt(now);
            Long handlerId = command.handlerId() != null ? command.handlerId()
                    : order.getHandlerId() != null ? order.getHandlerId()
                    : actor == null ? null : actor.id();
            order.setHandlerId(handlerId);
            if (note != null) {
                order.setDisposeNote(note);
            }
        } else if (event == OrderEvent.DISPOSE_DONE) {
            order.setDisposeNote(note);
        } else if (event == OrderEvent.CANNOT_DISPOSE) {
            order.setCloseReason(reason);
            if (note != null) {
                order.setDisposeNote(note);
            }
        } else if (event == OrderEvent.REVISIT_OK) {
            order.setCloseReason(reason != null ? reason : "回访确认恢复良好");
        } else if (event == OrderEvent.REVISIT_ANOMALY) {
            order.setDisposeNote(note);
        }

        order.setStatus(next);
        order.setUpdatedAt(now);
        orders.updateById(order);

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("status", before.name());
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("status", next.name());
        if (order.getAcceptorId() != null) {
            afterData.put("acceptorId", order.getAcceptorId());
        }
        if (order.getHandlerId() != null) {
            afterData.put("handlerId", order.getHandlerId());
        }
        if (order.getDisposeNote() != null) {
            afterData.put("disposeNote", order.getDisposeNote());
        }
        if (order.getCloseReason() != null) {
            afterData.put("closeReason", order.getCloseReason());
        }
        auditLog.successAs(actor == null ? 0L : actor.id(),
                actor == null || actor.primaryRole() == null ? "GUEST" : actor.primaryRole(),
                actionFor(event), BIZ_TYPE, order.getOrderNo(), beforeData, afterData);

        return new OrderTransitionResult(order.getOrderNo(), next, allowedTargets(next),
                order.getAcceptedAt(), toOffset(order.getDeadlineAt()),
                order.getHandlerId(), order.getDisposeNote(), order.getCloseReason());
    }

    public List<OrderStatus> allowedTargets(OrderStatus current) {
        if (current == null) {
            return List.of();
        }
        return StateMachines.order().allowedTargets(current).stream()
                .filter(status -> status != OrderStatus.TIMEOUT)
                .toList();
    }

    private OrderSummary toSummary(Order order, Report report, Map<Long, BasePoint> points, LocalDateTime now) {
        BasePoint point = report == null || report.getPointId() == null ? null : points.get(report.getPointId());
        return new OrderSummary(
                order.getOrderNo(),
                order.getReportId(),
                report == null ? null : report.getReportNo(),
                order.getType(),
                report == null ? null : report.getPointId(),
                point == null ? null : point.getPointName(),
                order.getStatus(),
                overdue(order, now),
                order.getCreatedAt(),
                toOffset(order.getDeadlineAt()),
                order.getAcceptedAt(),
                order.getHandlerId());
    }

    /**
     * "超时"只表示 24 小时受理时限：待受理超过 24 小时，或扫描任务已置 TIMEOUT。
     * 已受理工单即使处置超过 24 小时，也不应再算受理超时。
     */
    private boolean overdue(Order order, LocalDateTime now) {
        if (order.getStatus() == OrderStatus.TIMEOUT) {
            return true;
        }
        return order.getStatus() == OrderStatus.PENDING
                && order.getDeadlineAt() != null
                && order.getDeadlineAt().isBefore(now);
    }

    private OrderEvent eventFor(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return null;
        }
        return StateMachines.order().transitions().stream()
                .filter(transition -> transition.from() == current && transition.to() == target)
                .map(Transition::event)
                .findFirst()
                .orElse(null);
    }

    private StateTransitionException illegal(OrderStatus current, OrderStatus target) {
        return new StateTransitionException(ErrorCode.ORDER_STATE_INVALID, current, target, null,
                allowedTargets(current), StateMachines.order().allowedEvents(current));
    }

    private Order requireOrder(String orderNo) {
        String no = trimToNull(orderNo);
        if (no == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "工单号必填");
        }
        Order order = orders.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, no)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在：" + no);
        }
        return order;
    }

    private List<OrderTimelineItem> timeline(String orderNo) {
        List<SysLog> logs = sysLogs.selectList(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getBizType, BIZ_TYPE)
                .eq(SysLog::getBizId, orderNo)
                .eq(SysLog::getResult, "SUCCESS")
                .orderByAsc(SysLog::getCreatedAt)
                .orderByAsc(SysLog::getId));
        return logs.stream()
                .map(log -> new OrderTimelineItem(
                        log.getAction(),
                        actionLabel(log.getAction()),
                        log.getCreatedAt(),
                        log.getActorId(),
                        log.getActorRole(),
                        log.getResult(),
                        parseStatus(log.getBeforeJson()),
                        parseStatus(log.getAfterJson()),
                        parseNote(log.getAfterJson())))
                .toList();
    }

    private OrderStatus parseStatus(String json) {
        JsonNode root = parseJson(json);
        if (root == null || !root.hasNonNull("status")) {
            return null;
        }
        try {
            return OrderStatus.valueOf(root.get("status").asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String parseNote(String json) {
        JsonNode root = parseJson(json);
        if (root == null) {
            return null;
        }
        for (String field : List.of("closeReason", "disposeNote", "note")) {
            if (root.hasNonNull(field)) {
                return root.get(field).asText();
            }
        }
        return null;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String actionLabel(String action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case "REPORT_ACCEPT" -> "受理并转工单";
            case "ORDER_ACCEPT" -> "受理并指派处置人";
            case "ORDER_DISPOSE" -> "完成处置，转待回访";
            case "ORDER_CLOSE" -> "无法处置，关闭工单";
            case "ORDER_REVISIT_OK" -> "回访正常，关闭工单";
            case "ORDER_REVISIT_ANOMALY" -> "回访异常，转回处理中";
            case "ORDER_TIMEOUT" -> "超过二十四小时未受理";
            default -> action;
        };
    }

    private String actionFor(OrderEvent event) {
        return switch (event) {
            case ACCEPT -> "ORDER_ACCEPT";
            case DISPOSE_DONE -> "ORDER_DISPOSE";
            case CANNOT_DISPOSE -> "ORDER_CLOSE";
            case REVISIT_OK -> "ORDER_REVISIT_OK";
            case REVISIT_ANOMALY -> "ORDER_REVISIT_ANOMALY";
            case TIMEOUT -> "ORDER_TIMEOUT";
        };
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
