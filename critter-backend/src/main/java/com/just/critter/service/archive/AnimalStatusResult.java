package com.just.critter.service.archive;

import com.just.critter.domain.enums.AnimalStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 状态迁移结果。allowedStatuses 为新状态下的可迁移目标，前端据此刷新按钮。
 */
public record AnimalStatusResult(
        String code,
        AnimalStatus status,
        List<AnimalStatus> allowedStatuses,
        OffsetDateTime occurredAt) {
}
