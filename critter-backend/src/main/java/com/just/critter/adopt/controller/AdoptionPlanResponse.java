package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;

/**
 * 认养方案响应。金额单位为分；enabled 供管理员配置页使用，公开列表恒为 true。
 */
public record AdoptionPlanResponse(
        long planId,
        String planName,
        AdoptionPeriod period,
        int amount,
        String benefits,
        boolean enabled) {

    public static AdoptionPlanResponse from(AdoptionPlan plan) {
        return new AdoptionPlanResponse(
                plan.planId(), plan.planName(), plan.period(), plan.amount(),
                plan.benefits(), plan.enabled());
    }
}
