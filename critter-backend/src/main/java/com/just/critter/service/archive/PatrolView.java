package com.just.critter.service.archive;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 巡查记录视图。convertedSightingId 非空表示已转目击。
 */
public record PatrolView(
        Long id,
        PointRef point,
        OffsetDateTime patrolAt,
        Integer countEstimate,
        boolean hasAnomaly,
        String anomalyNote,
        List<String> fileIds,
        Long recorderId,
        Long convertedSightingId,
        OffsetDateTime createdAt) {
}
