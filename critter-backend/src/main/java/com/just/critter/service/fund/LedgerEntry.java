package com.just.critter.service.fund;

import java.time.OffsetDateTime;

/**
 * 台账逐笔流水。amount 始终为正数，方向由 type 区分，前端据此计算收支符号。
 */
public record LedgerEntry(
        String type,
        String refNo,
        String projectNo,
        String projectName,
        String title,
        String party,
        String animalCode,
        int amount,
        String status,
        OffsetDateTime occurredAt) {
}
