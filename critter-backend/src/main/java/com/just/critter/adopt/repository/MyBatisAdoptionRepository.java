package com.just.critter.adopt.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.service.AdoptionActivation;
import com.just.critter.adopt.service.AdoptionDetailRecord;
import com.just.critter.adopt.service.AdoptionPaymentRecord;
import com.just.critter.adopt.service.AdoptionPaymentWrite;
import com.just.critter.adopt.service.AdoptionTermination;
import com.just.critter.domain.model.AdoptionDetailRow;
import com.just.critter.domain.model.AdoptionEntity;
import com.just.critter.domain.model.AdoptionPaymentRow;
import com.just.critter.mapper.AdoptionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisAdoptionRepository implements AdoptionRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdoptionMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisAdoptionRepository(AdoptionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
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

    @Override
    public long count(Long userId, String status) {
        return mapper.count(userId, status);
    }

    @Override
    public List<AdoptionDetailRecord> findPage(Long userId, String status, long offset, int size) {
        return mapper.findPage(userId, status, offset, size).stream().map(this::toDetail).toList();
    }

    @Override
    public Optional<AdoptionDetailRecord> findDetail(String adoptionNo) {
        return Optional.ofNullable(mapper.findDetail(adoptionNo)).map(this::toDetail);
    }

    @Override
    public List<AdoptionPaymentRecord> findPayments(long adoptionId) {
        return mapper.findPayments(adoptionId).stream().map(this::toPayment).toList();
    }

    @Override
    public boolean hasPayment(long adoptionId) {
        return mapper.countVerifiedPayments(adoptionId) > 0;
    }

    @Override
    public int insertPayment(AdoptionPaymentWrite payment) {
        return mapper.insertPayment(payment.paymentNo(), payment.adoptionId(), payment.amount(),
                payment.paidAt(), payment.verifierId(), payment.verifiedAt(),
                writeVouchers(payment.voucherFileIds()), payment.note());
    }

    @Override
    public int activate(AdoptionActivation activation) {
        return mapper.activate(activation.adoptionId(), activation.certificateNo(),
                activation.startDate(), activation.endDate(), activation.updatedAt());
    }

    @Override
    public int terminate(AdoptionTermination termination) {
        return mapper.terminate(termination.adoptionId(), termination.reason(),
                termination.endDate(), termination.updatedAt());
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

    private AdoptionDetailRecord toDetail(AdoptionDetailRow row) {
        return new AdoptionDetailRecord(
                row.getId(),
                row.getAdoptionNo(),
                row.getUserId(),
                row.getUserName(),
                row.getAnimalCode(),
                row.getAnimalName(),
                row.getPlanId(),
                row.getPlanName(),
                row.getAmount(),
                AdoptionPeriod.valueOf(row.getPeriod()),
                AdoptionStatus.valueOf(row.getStatus()),
                row.getContact(),
                row.getAuditReason(),
                row.getStartDate(),
                row.getEndDate(),
                row.getCertificateNo(),
                row.getPayDeadline(),
                row.getCreatedAt());
    }

    private AdoptionPaymentRecord toPayment(AdoptionPaymentRow row) {
        return new AdoptionPaymentRecord(
                row.getPaymentNo(),
                row.getAmount(),
                row.getPaidAt(),
                readVouchers(row.getVoucherFileIds()),
                row.getNote(),
                row.getVerifiedAt());
    }

    private String writeVouchers(List<String> vouchers) {
        try {
            return objectMapper.writeValueAsString(vouchers == null ? List.of() : vouchers);
        } catch (Exception ex) {
            throw new IllegalStateException("缴费凭证序列化失败", ex);
        }
    }

    private List<String> readVouchers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
