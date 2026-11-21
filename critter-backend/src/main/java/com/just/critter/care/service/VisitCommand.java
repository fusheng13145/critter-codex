package com.just.critter.care.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理员登记就诊的输入。无工单时必须提供来源备注。
 */
public record VisitCommand(
        Long animalId,
        Long orderId,
        Long clinicId,
        OffsetDateTime visitedAt,
        String complaint,
        String diagnosis,
        String treatment,
        Integer cost,
        List<String> fileIds,
        String sourceNote) {
}
