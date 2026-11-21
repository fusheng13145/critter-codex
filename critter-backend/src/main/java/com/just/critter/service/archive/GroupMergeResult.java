package com.just.critter.service.archive;

import java.util.List;

/**
 * 群体合并结果。sourceCodes 子集仍保留在库，状态置为 GONE 并记录 mergedIntoCode。
 */
public record GroupMergeResult(
        String mainCode,
        List<String> mergedCodes,
        int minSize,
        int maxSize,
        List<Long> pointIds,
        int movedSightings) {
}
