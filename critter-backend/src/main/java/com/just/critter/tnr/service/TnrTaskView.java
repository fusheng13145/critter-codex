package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TNR 看板任务视图：任务头字段 + 全节点时间线。
 */
public record TnrTaskView(
        String tnrNo,
        long animalId,
        String animalCode,
        String animalName,
        TnrStage stage,
        long handlerId,
        String handlerName,
        LocalDateTime occurredAt,
        String note,
        List<String> fileIds,
        LocalDateTime createdAt,
        List<TnrNodeView> nodes) {
}
