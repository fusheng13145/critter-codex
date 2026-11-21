package com.just.critter.service.archive;

import com.just.critter.domain.enums.OccurrenceScale;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 出现记录视图，量级同时回枚举与中文名。
 */
public record OccurrenceView(
        Long id,
        Long speciesId,
        String speciesName,
        PointRef point,
        OffsetDateTime occurredAt,
        OccurrenceScale scale,
        String scaleLabel,
        String habitat,
        List<String> fileIds,
        Long recorderId,
        OffsetDateTime createdAt) {
}
