package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanWrite;
import com.just.critter.domain.model.AdoptionPlanEntity;
import com.just.critter.mapper.AdoptionPlanMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Override
    public AdoptionPlan insert(AdoptionPlanWrite write) {
        AdoptionPlanEntity entity = new AdoptionPlanEntity();
        entity.setPlanName(write.planName());
        entity.setPeriod(write.period().name());
        entity.setAmount(write.amount());
        entity.setBenefits(write.benefits());
        entity.setEnabled(write.enabled());
        mapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public int update(long planId, AdoptionPlanWrite write, LocalDateTime updatedAt) {
        AdoptionPlanEntity entity = new AdoptionPlanEntity();
        entity.setId(planId);
        entity.setPlanName(write.planName());
        entity.setPeriod(write.period().name());
        entity.setAmount(write.amount());
        entity.setBenefits(write.benefits());
        entity.setEnabled(write.enabled());
        return mapper.updateById(entity);
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
