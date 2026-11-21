package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;

/**
 * 关闭无效上报结果。
 */
public record ClosedReport(String reportNo, ReportStatus status, String closeReason) {
}
