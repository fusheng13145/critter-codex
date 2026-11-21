package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.domain.model.AdoptionPlanEntity;
import com.just.critter.mapper.AdoptionPlanMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAdoptionPlanRepository implements AdoptionPlanRepository {

    private final AdoptionPlanMapper mapper;

    public MyBatisAdoptionPlanRepository(AdoptionPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AdoptionPlan> findAll() {
        return mapper.selectList(null).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<AdoptionPlan> findById(long planId) {
        return Optional.ofNullable(mapper.selectById(planId)).map(this::toDomain);
    }

    private AdoptionPlan toDomain(AdoptionPlanEntity entity) {
        return new AdoptionPlan(
                entity.getId(),
                entity.getPlanName(),
                AdoptionPeriod.valueOf(entity.getPeriod()),
                entity.getAmount(),
                entity.getBenefits(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }
}
