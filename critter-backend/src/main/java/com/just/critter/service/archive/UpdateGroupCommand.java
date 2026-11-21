package com.just.critter.service.archive;

import java.util.List;

/**
 * 群体编辑命令；字段为 null 表示不改，规模区间按合并后的最终值校验。
 */
public record UpdateGroupCommand(
        String features,
        List<Long> pointIds,
        Integer minSize,
        Integer maxSize,
        String sizeBasis) {
}
