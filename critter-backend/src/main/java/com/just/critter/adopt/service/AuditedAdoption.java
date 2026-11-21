package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionStatus;

import java.time.OffsetDateTime;

/**
 * 审核回执：通过时带缴费截止时间，驳回时为空。
 */
public record AuditedAdoption(
        String adoptionNo,
        AdoptionStatus status,
        OffsetDateTime payDeadline) {
}
