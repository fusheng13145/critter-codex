package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点流水记录，供看板时间线组装。
 */
public record TnrNodeRecord(
        TnrStage stage,
        long handlerId,
        String handlerName,
        LocalDateTime occurredAt,
        String note,
        List<String> fileIds) {
}
