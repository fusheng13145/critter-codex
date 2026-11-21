package com.just.critter.service.archive;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 目击记录视图。groupCode 为空表示待归属，前端在独立列表高亮。
 */
public record SightingView(
        Long id,
        String groupCode,
        PointRef point,
        OffsetDateTime seenAt,
        Integer countEstimate,
        String behavior,
        List<String> fileIds,
        Long recorderId,
        String assignStatus,
        OffsetDateTime createdAt) {
}
