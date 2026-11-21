package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionPlan;

import java.util.List;
import java.util.Optional;

/**
 * 认养方案读取端口。
 */
public interface AdoptionPlanRepository {

    List<AdoptionPlan> findAll();

    Optional<AdoptionPlan> findById(long planId);
}
