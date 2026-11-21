package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanWrite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 认养方案读写端口。
 */
public interface AdoptionPlanRepository {

    List<AdoptionPlan> findAll();

    Optional<AdoptionPlan> findById(long planId);

    AdoptionPlan insert(AdoptionPlanWrite write);

    int update(long planId, AdoptionPlanWrite write, LocalDateTime updatedAt);
}
