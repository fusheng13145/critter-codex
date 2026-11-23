package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;

import java.time.OffsetDateTime;

/**
 * 关闭上报结果：无效上报直接结案；有效关闭会写入确认截止时间，等待上报人确认。
 */
public record ClosedReport(
        String reportNo,
        ReportStatus status,
        String closeReason,
        boolean invalid,
        OffsetDateTime confirmDueAt) {
}
