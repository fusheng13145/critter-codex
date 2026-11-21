package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工单详情：allowed 供前端禁用按钮，timeline 来自操作日志，不额外造事件表。
 */
public record OrderDetail(
        String orderNo,
        Long reportId,
        String reportNo,
        ReportType type,
        Long pointId,
        String pointName,
        String description,
        List<String> photos,
        Long animalId,
        String animalCode,
        OrderStatus status,
        boolean overdue,
        LocalDateTime createdAt,
        OffsetDateTime deadlineAt,
        LocalDateTime acceptedAt,
        Long acceptorId,
        Long handlerId,
        String disposeNote,
        String closeReason,
        List<OrderStatus> allowed,
        List<OrderTimelineItem> timeline) {
}
