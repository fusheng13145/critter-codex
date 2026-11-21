package com.just.critter.adopt.repository;

import com.just.critter.adopt.service.ExpiringAdoption;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 到期任务专用查询：只读写 ACTIVE 认养与其动物，避免影响申请/缴费主链路。
 */
public interface AdoptionExpiryRepository {

    List<ExpiringAdoption> findExpiring(LocalDate endDate);

    List<ExpiringAdoption> findExpired(LocalDate today);

    int expire(long adoptionId, LocalDateTime now);

    int releaseAnimal(long animalId, LocalDateTime now);
}
