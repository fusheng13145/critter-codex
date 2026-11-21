package com.just.critter.job.rescue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.just.critter.domain.enums.OrderEvent;
import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.support.log.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单超时扫描：每小时把超过受理时限的 PENDING 工单置为 TIMEOUT。
 *
 * <p>条件更新保证多实例幂等：只有仍为 PENDING 且 deadline_at 已过的行才会命中，
 * 两个实例同时扫描也只有一个能写成功，另一个不会重复记审计。</p>
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);
    private static final String BIZ_TYPE = "RESCUE_ORDER";

    private final OrderMapper orders;
    private final AuditLogService auditLog;
    private final Clock clock;

    public OrderTimeoutJob(OrderMapper orders, AuditLogService auditLog, Clock clock) {
        this.orders = orders;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${critter.jobs.order-timeout-initial-delay-ms:60000}",
            fixedDelayString = "${critter.jobs.order-timeout-interval-ms:3600000}")
    @Transactional
    public OrderTimeoutScanResult scan() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<Order> candidates = orders.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING)
                .lt(Order::getDeadlineAt, now)
                .orderByAsc(Order::getDeadlineAt)
                .last("LIMIT 500"));
        int timedOut = 0;
        for (Order order : candidates) {
            OrderStatus next = StateMachines.order().fire(OrderStatus.PENDING, OrderEvent.TIMEOUT,
                    TransitionContext.of(Role.SYSTEM));
            int updated = orders.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, order.getId())
                    .eq(Order::getStatus, OrderStatus.PENDING)
                    .set(Order::getStatus, next)
                    .set(Order::getUpdatedAt, now));
            if (updated == 0) {
                continue;
            }
            timedOut++;
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", OrderStatus.PENDING.name());
            before.put("deadlineAt", order.getDeadlineAt());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", next.name());
            after.put("scannedAt", now);
            auditLog.successAs(0L, Role.SYSTEM.name(), "ORDER_TIMEOUT", BIZ_TYPE,
                    order.getOrderNo(), before, after);
        }
        if (!candidates.isEmpty()) {
            log.info("order timeout scan candidates={} timedOut={}", candidates.size(), timedOut);
        }
        return new OrderTimeoutScanResult(candidates.size(), timedOut);
    }
}
