package com.just.critter.service.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;

import java.time.OffsetDateTime;

/**
 * 列表行，字段与 03 §7.2 示例一致。gender 以枚举名输出，前端用字典映射中文。
 */
public record AnimalSummary(
        String code,
        AnimalCategory category,
        String name,
        String gender,
        String color,
        Integer estimatedAge,
        PointRef point,
        AnimalStatus status,
        boolean neutered,
        boolean adopted,
        String coverUrl,
        OffsetDateTime updatedAt) {
}
