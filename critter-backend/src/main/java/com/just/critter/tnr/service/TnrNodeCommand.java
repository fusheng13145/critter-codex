package com.just.critter.tnr.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 推进 TNR 节点的输入。stage 必须是当前节点的下一节点。
 */
public record TnrNodeCommand(
        String stage,
        Long handlerId,
        OffsetDateTime occurredAt,
        String note,
        List<String> fileIds) {
}
