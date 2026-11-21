package com.just.critter.service.archive;

import java.time.OffsetDateTime;

/**
 * 字段级变更历史行。
 */
public record AnimalChangeLogView(
        String field,
        String beforeValue,
        String afterValue,
        String reason,
        Long operatorId,
        OffsetDateTime createdAt) {
}
