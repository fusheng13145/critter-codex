package com.just.critter.service.archive;

import com.just.critter.domain.enums.OccurrenceScale;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 新增出现记录命令。数量用枚举，不接受数字；照片必填。
 */
public record CreateOccurrenceCommand(
        Long speciesId,
        Long pointId,
        OffsetDateTime occurredAt,
        OccurrenceScale scale,
        String habitat,
        List<String> fileIds) {
}
