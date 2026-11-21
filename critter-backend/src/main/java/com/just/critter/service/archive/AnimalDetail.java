package com.just.critter.service.archive;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 档案详情。internalNote 仅管理员视图返回，公开视图序列化时省略该字段。
 * allowedStatuses 是状态机允许的目标状态，前端据此渲染迁移按钮。
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
        List<AnimalStatus> allowedStatuses,
        boolean neutered,
        LocalDate neuteredAt,
        boolean adopted,
        String coverUrl,
        String internalNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
