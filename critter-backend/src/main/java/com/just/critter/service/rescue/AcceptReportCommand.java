package com.just.critter.service.rescue;

/**
 * 受理上报入参：处置人、可选关联档案、处置备注。
 */
public record AcceptReportCommand(Long handlerId, String animalCode, String note) {
}
