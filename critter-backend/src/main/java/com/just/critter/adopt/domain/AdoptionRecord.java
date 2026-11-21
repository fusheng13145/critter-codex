package com.just.critter.adopt.domain;

import java.time.LocalDateTime;

/**
 * 认养单领域对象。金额单位为分，方案金额与周期在提交时快照，后续改方案不影响旧单。
 */
public record AdoptionRecord(
        long id,
        String adoptionNo,
        long userId,
        long animalId,
        String animalCode,
        long planId,
        int amount,
        AdoptionPeriod period,
        AdoptionStatus status,
        String contact,
        String remark,
        LocalDateTime promiseAcceptedAt) {
}
