package com.just.critter.controller.archive;

import com.just.critter.service.archive.CreateGroupCommand;

import java.util.List;

/**
 * 群体建档请求。规模用 minSize / maxSize 表示闭区间。
 */
public record CreateGroupRequest(
        Long speciesId,
        String features,
        List<Long> pointIds,
        int minSize,
        int maxSize,
        String sizeBasis) {

    public CreateGroupCommand toCommand() {
        return new CreateGroupCommand(speciesId, features, pointIds, minSize, maxSize, sizeBasis);
    }
}
