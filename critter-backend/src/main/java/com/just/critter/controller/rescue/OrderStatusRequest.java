package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.service.rescue.OrderStatusCommand;

/**
 * 工单状态流转请求：targetStatus 必填，note/reason 按事件要求填写。
 */
public record OrderStatusRequest(String targetStatus, String note, String reason, Long handlerId) {

    public OrderStatusCommand toCommand(OrderStatus target) {
        return new OrderStatusCommand(target, note, reason, handlerId);
    }
}
