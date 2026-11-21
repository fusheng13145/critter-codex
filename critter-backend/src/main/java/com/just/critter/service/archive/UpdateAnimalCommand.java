package com.just.critter.service.archive;

import com.just.critter.domain.enums.Gender;

/**
 * 编辑入参：字段为 null 表示不改；reason 必填且不少于 5 字。
 */
public record UpdateAnimalCommand(
        String name,
        Gender gender,
        String color,
        Integer estimatedAge,
        String features,
        Long pointId,
        String internalNote,
        String reason) {
}
