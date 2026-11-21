package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;

/**
 * 公开认养方案响应。金额单位为分。
 */
public record AdoptionPlanResponse(
        long planId,
        String planName,
        AdoptionPeriod period,
        int amount,
        String benefits) {

    public static AdoptionPlanResponse from(AdoptionPlan plan) {
        return new AdoptionPlanResponse(
                plan.planId(), plan.planName(), plan.period(), plan.amount(), plan.benefits());
    }
}
