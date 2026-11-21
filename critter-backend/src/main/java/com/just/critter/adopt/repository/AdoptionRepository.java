package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 认养单持久化端口：动物行锁之后的生效关系查询与本单写入必须处于同一事务。
 */
public interface AdoptionRepository {

    Optional<String> findActiveAdoptionNo(long animalId);

    long countActiveByUser(long userId);

    void save(AdoptionRecord record);

    Optional<AdoptionRecord> findByNoForUpdate(String adoptionNo);

    Optional<String> findBlockingAdoptionNo(long animalId, String excludedAdoptionNo);

    int updateAudit(String adoptionNo,
                    AdoptionStatus status,
                    long auditorId,
                    String reason,
                    int amount,
                    LocalDateTime auditedAt);
}
