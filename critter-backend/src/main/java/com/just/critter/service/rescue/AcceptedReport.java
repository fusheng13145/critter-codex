package com.just.critter.service.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportStatus;

import java.time.OffsetDateTime;

/**
 * 受理结果：上报转 PROCESSING，工单同时为 PROCESSING。
 */
public record AcceptedReport(
        String orderNo,
        String reportNo,
        ReportStatus reportStatus,
        OrderStatus orderStatus,
        OffsetDateTime deadline) {
}
