package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;

import java.time.OffsetDateTime;

/**
 * 上报创建结果，对应 03 §7.5 响应 data。
 * duplicated=true 时 duplicateOf 为可合并的原上报编号；新单本身仍已落库。
 */
public record CreatedReport(
        String reportNo,
        ReportStatus status,
        OffsetDateTime deadline,
        boolean duplicated,
        String duplicateOf) {
}
