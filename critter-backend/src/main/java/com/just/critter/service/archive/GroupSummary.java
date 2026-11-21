package com.just.critter.service.archive;

import com.just.critter.domain.enums.GroupStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 群体摘要，规模始终以区间返回，前端不得自行取中位数当事实。
 */
public record GroupSummary(
        String groupCode,
        SpeciesRef species,
        String features,
        int minSize,
        int maxSize,
        String sizeBasis,
        GroupStatus status,
        String statusLabel,
        List<PointRef> points,
        OffsetDateTime lastSeenAt) {
}
