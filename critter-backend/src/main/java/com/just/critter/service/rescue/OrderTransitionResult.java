package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 流转结果：allowed 是流转后的可选目标，终态时为空。
 */
public record OrderTransitionResult(
        String orderNo,
        OrderStatus status,
        List<OrderStatus> allowed,
        LocalDateTime acceptedAt,
        OffsetDateTime deadlineAt,
        Long handlerId,
        String disposeNote,
        String closeReason) {
}
