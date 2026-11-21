package com.just.critter.tnr.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理员发起 TNR 的输入；首节点固定为 CATCH，handlerId 为空时取当前操作人。
 */
public record TnrCommand(
        Long animalId,
        Long handlerId,
        OffsetDateTime occurredAt,
        String note,
        List<String> fileIds) {
}
