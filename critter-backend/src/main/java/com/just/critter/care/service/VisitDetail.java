package com.just.critter.care.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 就诊详情：包含诊断、处置、费用与影像文件编号。
 */
public record VisitDetail(
        String visitNo,
        long animalId,
        String animalCode,
        String animalName,
        Long orderId,
        String orderNo,
        long clinicId,
        String clinicName,
        LocalDateTime visitedAt,
        String complaint,
        String diagnosis,
        String treatment,
        int cost,
        List<String> fileIds,
        String sourceNote,
        LocalDateTime createdAt) {
}
