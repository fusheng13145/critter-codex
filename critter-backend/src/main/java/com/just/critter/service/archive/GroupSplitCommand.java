package com.just.critter.service.archive;

import java.util.List;

/**
 * 群体拆分命令。源群体保留，新群体编号由服务端生成。
 */
public record GroupSplitCommand(
        String sourceCode,
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
}
