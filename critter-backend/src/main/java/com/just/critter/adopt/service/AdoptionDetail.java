package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdoptionDetail(
        String adoptionNo,
        long userId,
        String userName,
        String animalCode,
        String animalName,
        String planName,
        int amount,
        AdoptionPeriod period,
        AdoptionStatus status,
        String contactMasked,
        String auditReason,
        LocalDate startDate,
        LocalDate endDate,
        String certificateNo,
        LocalDateTime payDeadline,
        LocalDateTime createdAt,
        List<AdoptionPaymentRecord> payments) {
}
