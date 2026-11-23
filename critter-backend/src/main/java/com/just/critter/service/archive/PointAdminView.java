package com.just.critter.service.archive;

import java.math.BigDecimal;

/**
 * 点位维护视图：在公开点位字段上追加引用统计与是否可删。
 */
public record PointAdminView(
        Long id,
        String name,
        String area,
        BigDecimal x,
        BigDecimal y,
        boolean enabled,
        String note,
        long animalCount,
        long groupCount,
        long sightingCount,
        long occurrenceCount,
        long reportCount,
        long patrolCount,
        long referenceCount,
        boolean deletable) {
}
