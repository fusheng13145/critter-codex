package com.just.critter.controller.archive;

import com.just.critter.service.archive.CreateSightingCommand;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 目击登记请求；数量是估计值，照片可选。
 */
public record CreateSightingRequest(
        Long pointId,
        OffsetDateTime seenAt,
        Integer countEstimate,
        String behavior,
        List<String> fileIds) {

    public CreateSightingCommand toCommand() {
        return new CreateSightingCommand(pointId, seenAt, countEstimate, behavior, fileIds);
    }
}
