package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.domain.model.AdoptionEntity;
import com.just.critter.mapper.AdoptionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MyBatisAdoptionRepository implements AdoptionRepository {

    private final AdoptionMapper mapper;

    public MyBatisAdoptionRepository(AdoptionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findActiveAdoptionNo(long animalId) {
        return Optional.ofNullable(mapper.findActiveAdoptionNo(animalId));
    }

    @Override
    public long countActiveByUser(long userId) {
        return mapper.countActiveByUser(userId);
    }

    @Override
    public void save(AdoptionRecord record) {
        AdoptionEntity entity = new AdoptionEntity();
        entity.setAdoptionNo(record.adoptionNo());
        entity.setUserId(record.userId());
        entity.setAnimalId(record.animalId());
        entity.setAnimalCode(record.animalCode());
        entity.setPlanId(record.planId());
        entity.setAmount(record.amount());
        entity.setPeriod(record.period().name());
        entity.setStatus(record.status().name());
        entity.setContact(record.contact());
        entity.setRemark(record.remark());
        entity.setPromiseAcceptedAt(record.promiseAcceptedAt());
        entity.setCreatedAt(record.promiseAcceptedAt());
        entity.setUpdatedAt(record.promiseAcceptedAt());
        entity.setCreatedBy(record.userId());
        mapper.insert(entity);
    }

    @Override
    public Optional<AdoptionRecord> findByNoForUpdate(String adoptionNo) {
        return Optional.ofNullable(mapper.findByNoForUpdate(adoptionNo)).map(this::toRecord);
    }

    @Override
    public Optional<String> findBlockingAdoptionNo(long animalId, String excludedAdoptionNo) {
        return Optional.ofNullable(mapper.findBlockingAdoptionNo(animalId, excludedAdoptionNo));
    }

    @Override
    public int updateAudit(String adoptionNo,
                           AdoptionStatus status,
                           long auditorId,
                           String reason,
                           int amount,
                           LocalDateTime auditedAt) {
        return mapper.updateAudit(adoptionNo, status.name(), auditorId, reason, amount, auditedAt);
    }

    private AdoptionRecord toRecord(AdoptionEntity entity) {
        return new AdoptionRecord(
                entity.getId(),
                entity.getAdoptionNo(),
                entity.getUserId(),
                entity.getAnimalId(),
                entity.getAnimalCode(),
                entity.getPlanId(),
                entity.getAmount(),
                AdoptionPeriod.valueOf(entity.getPeriod()),
                AdoptionStatus.valueOf(entity.getStatus()),
                entity.getContact(),
                entity.getRemark(),
                entity.getPromiseAcceptedAt());
    }
}
