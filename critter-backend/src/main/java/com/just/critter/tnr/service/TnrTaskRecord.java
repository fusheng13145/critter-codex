package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务头记录，供服务判断当前节点并组装视图。
 */
public record TnrTaskRecord(
        String tnrNo,
        long animalId,
        String animalCode,
        String animalName,
        TnrStage stage,
        long handlerId,
        String handlerName,
        LocalDateTime occurredAt,
        List<String> fileIds,
        String note,
        LocalDateTime createdAt) {
}
