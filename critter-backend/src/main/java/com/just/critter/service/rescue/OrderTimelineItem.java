package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;

import java.time.LocalDateTime;

/**
 * 工单时间线：直接投影 sys_log 中 biz_type=RESCUE_ORDER、biz_id=工单号的成功记录。
 */
public record OrderTimelineItem(
        String action,
        String actionLabel,
        LocalDateTime at,
        Long actorId,
        String actorRole,
        String result,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String note) {
}
