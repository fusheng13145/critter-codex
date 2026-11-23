package com.just.critter.service.rescue;

import java.time.OffsetDateTime;

/**
 * 上报人确认结案结果。
 */
public record ReportConfirmation(
        String reportNo,
        String status,
        OffsetDateTime confirmedAt,
        boolean autoConfirmed) {
}
