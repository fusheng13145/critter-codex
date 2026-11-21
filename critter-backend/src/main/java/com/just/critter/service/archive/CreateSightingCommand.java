package com.just.critter.service.archive;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 目击登记命令。数量是估计值，可空但不接受负数；照片可选。
 */
public record CreateSightingCommand(
        Long pointId,
        OffsetDateTime seenAt,
        Integer countEstimate,
        String behavior,
        List<String> fileIds) {
}
