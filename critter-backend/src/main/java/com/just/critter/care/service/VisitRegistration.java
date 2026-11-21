package com.just.critter.care.service;

import java.time.LocalDateTime;

/**
 * 就诊登记结果：前端凭 visitNo 查询详情或再次打印。
 */
public record VisitRegistration(
        String visitNo,
        long animalId,
        String animalCode,
        String animalName,
        long clinicId,
        String clinicName,
        LocalDateTime visitedAt,
        int cost) {
}
