package com.just.critter.tnr.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TNR 节点视图，按发生时间顺序展示每个责任人与附件。
 */
public record TnrNodeView(
        TnrStage stage,
        long handlerId,
        String handlerName,
        LocalDateTime occurredAt,
        String note,
        List<String> fileIds) {
}
