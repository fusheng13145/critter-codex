package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;

/**
 * 工单流转命令：targetStatus 决定迁移事件，note/reason 分别落 dispose_note/close_reason。
 */
public record OrderStatusCommand(OrderStatus targetStatus, String note, String reason, Long handlerId) {
}
