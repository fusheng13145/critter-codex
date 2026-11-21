package com.just.critter.adopt.domain;

/**
 * 认养方案领域对象。金额单位为分。
 */
public record AdoptionPlan(
        long planId,
        String planName,
        AdoptionPeriod period,
        int amount,
        String benefits,
        boolean enabled) {
}
