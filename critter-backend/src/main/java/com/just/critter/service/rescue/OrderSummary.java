package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 工单看板行：列表不返回描述与联系方式，只保留受理所需的定位信息。
 */
public record OrderSummary(
        String orderNo,
        Long reportId,
        String reportNo,
        ReportType type,
        Long pointId,
        String pointName,
        OrderStatus status,
        boolean overdue,
        LocalDateTime createdAt,
        OffsetDateTime deadlineAt,
        LocalDateTime acceptedAt,
        Long handlerId) {
}
