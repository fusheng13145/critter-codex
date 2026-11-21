package com.just.critter.service.archive;

import com.just.critter.domain.enums.AnimalEvent;
import com.just.critter.domain.enums.AnimalStatus;

import java.time.OffsetDateTime;

/**
 * 状态时间线一行。事件与状态同时给 code 与中文 label，前端无需再维护字典。
 */
public record AnimalStatusLogView(
        AnimalStatus fromStatus,
        String fromLabel,
        AnimalStatus toStatus,
        String toLabel,
        AnimalEvent event,
        String eventLabel,
        String reason,
        OffsetDateTime occurredAt) {
}
