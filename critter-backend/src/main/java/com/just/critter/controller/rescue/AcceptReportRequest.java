package com.just.critter.controller.rescue;

import com.just.critter.service.rescue.AcceptReportCommand;

/**
 * 受理上报请求：处置人可不填（默认当前管理员），animalCode 可选。
 */
public record AcceptReportRequest(Long handlerId, String animalCode, String note) {

    public AcceptReportCommand toCommand() {
        return new AcceptReportCommand(handlerId, animalCode, note);
    }
}
