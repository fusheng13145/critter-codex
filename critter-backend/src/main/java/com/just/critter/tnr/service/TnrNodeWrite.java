package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TNR 单节点落库快照，同时用于更新任务头与追加节点流水。
 */
public record TnrNodeWrite(
        String tnrNo,
        TnrStage stage,
        long handlerId,
        LocalDateTime occurredAt,
        List<String> fileIds,
        String note,
        LocalDateTime createdAt,
        long createdBy) {
}
