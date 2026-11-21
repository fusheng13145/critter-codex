package com.just.critter.controller.archive;

import com.just.critter.domain.enums.Gender;
import com.just.critter.service.archive.UpdateAnimalCommand;

/**
 * 编辑请求体：字段缺省表示不改，reason 必填。
 */
public record UpdateAnimalRequest(
        String name,
        Gender gender,
        String color,
        Integer estimatedAge,
        String features,
        Long pointId,
        String internalNote,
        String reason) {

    public UpdateAnimalCommand toCommand() {
        return new UpdateAnimalCommand(name, gender, color, estimatedAge, features, pointId,
                internalNote, reason);
    }
}
