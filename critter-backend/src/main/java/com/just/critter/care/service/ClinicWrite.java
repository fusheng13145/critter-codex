package com.just.critter.care.service;

import java.time.LocalDateTime;

/**
 * 合作医院落库快照。clinicId 由数据库自增生成。
 */
public record ClinicWrite(
        String clinicName,
        String contact,
        String phone,
        String address,
        String priceNote,
        LocalDateTime createdAt,
        long createdBy) {
}
