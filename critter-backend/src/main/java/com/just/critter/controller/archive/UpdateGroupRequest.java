package com.just.critter.controller.archive;

import com.just.critter.service.archive.UpdateGroupCommand;

import java.util.List;

/**
 * 群体编辑请求；未传字段表示不改。
 */
public record UpdateGroupRequest(
        String features,
        List<Long> pointIds,
        Integer minSize,
        Integer maxSize,
        String sizeBasis) {

    public UpdateGroupCommand toCommand() {
        return new UpdateGroupCommand(features, pointIds, minSize, maxSize, sizeBasis);
    }
}
