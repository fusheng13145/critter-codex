package com.just.critter.adopt.service;

/**
 * 查询仓储返回的认养单扁平记录。contact 为原始值，脱敏只在服务层做。
 */
public record AdoptionDetailRecord(
        long id,
        String adoptionNo,
        long userId,
        String userName,
        String animalCode,
        String animalName,
        long planId,
        String planName,
        int amount,
        com.just.critter.adopt.domain.AdoptionPeriod period,
        com.just.critter.adopt.domain.AdoptionStatus status,
        String contact,
        String auditReason,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate,
        String certificateNo,
        java.time.LocalDateTime payDeadline,
        java.time.LocalDateTime createdAt) {
}
