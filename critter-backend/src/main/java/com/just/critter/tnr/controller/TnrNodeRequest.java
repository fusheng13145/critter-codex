package com.just.critter.tnr.controller;

import com.just.critter.tnr.service.TnrNodeCommand;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 推进 TNR 节点请求。stage 只能是当前节点的下一节点。
 */
public record TnrNodeRequest(
        String stage,
        Long handlerId,
        OffsetDateTime occurredAt,
        String note,
        List<String> fileIds) {

    public TnrNodeCommand toCommand() {
        return new TnrNodeCommand(stage, handlerId, occurredAt, note, fileIds);
    }
}
