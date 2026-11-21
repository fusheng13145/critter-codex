package com.just.critter.service.archive;

/**
 * 群体拆分结果。movedSightings 为成功转移到新群体的目击条数。
 */
public record GroupSplitResult(
        String sourceCode,
        String newCode,
        int sourceMinSize,
        int sourceMaxSize,
        int newMinSize,
        int newMaxSize,
        int movedSightings) {
}
