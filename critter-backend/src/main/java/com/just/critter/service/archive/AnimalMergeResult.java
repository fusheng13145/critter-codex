package com.just.critter.service.archive;

import java.util.List;

/**
 * 合并结果。mergedCodes 为并入的副编号，aliases 已落 animal_alias。
 */
public record AnimalMergeResult(
        String mainCode,
        List<String> mergedCodes,
        List<String> aliases,
        int movedHealthRecords) {
}
