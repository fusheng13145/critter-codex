package com.just.critter.service.archive;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 档案详情。internalNote 仅管理员视图返回，公开视图序列化时省略该字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnimalDetail(
        String code,
        AnimalCategory category,
        String name,
        String gender,
        String color,
        Integer estimatedAge,
        String features,
        PointRef point,
        AnimalStatus status,
        boolean neutered,
        LocalDate neuteredAt,
        boolean adopted,
        String coverUrl,
        String internalNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
