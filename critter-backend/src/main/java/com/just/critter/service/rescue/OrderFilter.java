package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;

import java.util.List;

/**
 * 工单列表筛选：状态可多选，类型沿用上报类型，overdue 只看"待受理超过 24 小时或已超时"。
 */
public record OrderFilter(List<OrderStatus> statuses, ReportType type, Boolean overdue) {
}
