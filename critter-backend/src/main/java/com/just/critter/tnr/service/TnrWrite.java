package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TNR 任务头落库快照。tnr_task 保存当前节点，节点流水另写 tnr_node。
 */
public record TnrWrite(
        String tnrNo,
        long animalId,
        TnrStage stage,
        long handlerId,
        LocalDateTime occurredAt,
        List<String> fileIds,
        String note,
        LocalDateTime createdAt,
        long createdBy) {
}
