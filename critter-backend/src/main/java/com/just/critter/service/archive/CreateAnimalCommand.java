package com.just.critter.service.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.Gender;

import java.util.List;

/**
 * 建档入参。clientCode 仅用于识别前端是否越权传编号，不参与落库。
 */
public record CreateAnimalCommand(
        AnimalCategory category,
        Gender gender,
        String color,
        Integer estimatedAge,
        String features,
        Long pointId,
        List<String> photos,
        String healthNote,
        String clientCode) {
}
