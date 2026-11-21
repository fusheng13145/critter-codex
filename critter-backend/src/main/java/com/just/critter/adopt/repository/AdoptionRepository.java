package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionActivation;
import com.just.critter.adopt.service.AdoptionDetailRecord;
import com.just.critter.adopt.service.AdoptionPaymentRecord;
import com.just.critter.adopt.service.AdoptionPaymentWrite;
import com.just.critter.adopt.service.AdoptionTermination;

import java.time.LocalDateTime;
import java.util.List;
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

    long count(Long userId, String status);

    List<AdoptionDetailRecord> findPage(Long userId, String status, long offset, int size);

    Optional<AdoptionDetailRecord> findDetail(String adoptionNo);

    List<AdoptionPaymentRecord> findPayments(long adoptionId);

    boolean hasPayment(long adoptionId);

    int insertPayment(AdoptionPaymentWrite payment);

    int activate(AdoptionActivation activation);

    int terminate(AdoptionTermination termination);
}
