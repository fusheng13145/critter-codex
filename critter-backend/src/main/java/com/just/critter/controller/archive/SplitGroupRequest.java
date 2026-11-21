package com.just.critter.controller.archive;

import com.just.critter.service.archive.GroupSplitCommand;

import java.util.List;

/**
 * 群体拆分请求。源群体保留并缩小范围，新群体使用 new* 字段；moveSightingIds 可选。
 */
public record SplitGroupRequest(
        String newFeatures,
        Integer newMinSize,
        Integer newMaxSize,
        String newSizeBasis,
        List<Long> pointIds,
        Integer sourceMinSize,
        Integer sourceMaxSize,
        String sourceSizeBasis,
        List<Long> moveSightingIds,
        String reason) {

    public GroupSplitCommand toCommand(String sourceCode) {
        return new GroupSplitCommand(sourceCode, newFeatures, newMinSize, newMaxSize, newSizeBasis,
                pointIds, sourceMinSize, sourceMaxSize, sourceSizeBasis, moveSightingIds, reason);
    }
}
