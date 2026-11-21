package com.just.critter.service.archive;

import com.just.critter.domain.enums.GroupStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 群体详情：物种、点位集合、规模区间与最近目击时间。
 */
public record GroupDetail(
        String groupCode,
        SpeciesRef species,
        String features,
        int minSize,
        int maxSize,
        String sizeBasis,
        GroupStatus status,
        String statusLabel,
        List<PointRef> points,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
