package com.just.critter.service.fund;

import java.time.OffsetDateTime;

/**
 * 结项公示追加说明单条视图。
 */
public record SettlementNoteView(String note, Long operatorId, OffsetDateTime createdAt) {
}
