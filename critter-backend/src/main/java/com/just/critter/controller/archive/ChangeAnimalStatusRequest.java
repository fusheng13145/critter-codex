package com.just.critter.controller.archive;

import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.service.archive.AnimalStatusChangeCommand;

/**
 * 状态迁移入参。targetStatus 为目标状态，reason 至少 5 字，用于审计追溯。
 */
public record ChangeAnimalStatusRequest(AnimalStatus targetStatus, String reason) {

    public AnimalStatusChangeCommand toCommand() {
        return new AnimalStatusChangeCommand(targetStatus, reason);
    }
}
