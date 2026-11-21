package com.just.critter.controller.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.Gender;
import com.just.critter.service.archive.CreateAnimalCommand;

import java.util.List;

/**
 * 建档请求体，对应 03 §7.3。code 字段只用于检测前端越权传编号。
 */
public record CreateAnimalRequest(
        AnimalCategory category,
        Gender gender,
        String color,
        Integer estimatedAge,
        String features,
        Long pointId,
        List<String> photos,
        String healthNote,
        String code) {

    public CreateAnimalCommand toCommand() {
        return new CreateAnimalCommand(category, gender, color, estimatedAge, features, pointId,
                photos, healthNote, code);
    }
}
