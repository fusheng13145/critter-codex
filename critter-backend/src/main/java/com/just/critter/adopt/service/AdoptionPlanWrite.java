package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionPeriod;

import java.time.LocalDateTime;

public record AdoptionPlanWrite(String planName, AdoptionPeriod period, int amount,
                                String benefits, boolean enabled, LocalDateTime createdAt,
                                long createdBy) {
}
