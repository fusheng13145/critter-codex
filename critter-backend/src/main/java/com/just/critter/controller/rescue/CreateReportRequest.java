package com.just.critter.controller.rescue;

import com.just.critter.service.rescue.CreateReportCommand;

import java.util.List;

/**
 * 上报请求体，对应 03 §7.5。type 用字符串接收，枚举错误由服务层统一回 30303。
 */
public record CreateReportRequest(
        String type,
        Long pointId,
        List<String> photos,
        String description,
        String animalCode,
        Long speciesId,
        String contact) {

    public CreateReportCommand toCommand() {
        return new CreateReportCommand(type, pointId, photos, description, animalCode, speciesId, contact);
    }
}
