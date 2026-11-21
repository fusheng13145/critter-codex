package com.just.critter.service.archive;

import com.just.critter.domain.enums.AnimalStatus;

/**
 * 状态迁移入参。状态机按目标状态反查事件，调用方不直接指定事件。
 */
public record AnimalStatusChangeCommand(AnimalStatus targetStatus, String reason) {
}
