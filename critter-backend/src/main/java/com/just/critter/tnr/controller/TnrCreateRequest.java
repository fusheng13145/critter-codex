package com.just.critter.tnr.controller;

import com.just.critter.tnr.service.TnrCommand;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 发起 TNR 请求。首节点固定为 CATCH。
 */
public record TnrCreateRequest(
        Long animalId,
        Long handlerId,
        OffsetDateTime occurredAt,
        String note,
        List<String> fileIds) {

    public TnrCommand toCommand() {
        return new TnrCommand(animalId, handlerId, occurredAt, note, fileIds);
    }
}
