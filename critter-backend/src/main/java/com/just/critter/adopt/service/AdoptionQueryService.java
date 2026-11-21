package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class AdoptionQueryService {

    private final AdoptionRepository adoptions;

    public AdoptionQueryService(AdoptionRepository adoptions) {
        this.adoptions = adoptions;
    }

    public PageResult<AdoptionSummary> list(String status, PageQuery query) {
        return page(null, status, query);
    }

    public PageResult<AdoptionSummary> mine(long userId, String status, PageQuery query) {
        return page(userId, status, query);
    }

    public AdoptionDetail detail(String adoptionNo, long requesterId, boolean admin) {
        AdoptionDetailRecord record = require(adoptionNo);
        if (!admin && record.userId() != requesterId) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, Map.of("adoptionNo", adoptionNo));
        }
        return new AdoptionDetail(
                record.adoptionNo(),
                record.userId(),
                record.userName(),
                record.animalCode(),
                record.animalName(),
                record.planName(),
                record.amount(),
                record.period(),
                record.status(),
                admin ? record.contact() : mask(record.contact()),
                record.auditReason(),
                record.startDate(),
                record.endDate(),
                record.certificateNo(),
                record.payDeadline(),
                record.createdAt(),
                adoptions.findPayments(record.id()));
    }

    public CertificateView certificate(String adoptionNo, long requesterId, boolean admin) {
        AdoptionDetailRecord record = require(adoptionNo);
        if (!admin && record.userId() != requesterId) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, Map.of("adoptionNo", adoptionNo));
        }
        if (record.certificateNo() == null || record.startDate() == null || record.endDate() == null
                || (record.status() != AdoptionStatus.ACTIVE
                && record.status() != AdoptionStatus.TERMINATED)) {
            throw new BusinessException(ErrorCode.PAYMENT_UNVERIFIED,
                    Map.of("adoptionNo", adoptionNo, "status", record.status().name()));
        }
        return new CertificateView(record.adoptionNo(), record.certificateNo(), record.userName(),
                record.animalCode(), record.animalName(), record.planName(),
                record.startDate(), record.endDate());
    }

    private PageResult<AdoptionSummary> page(Long userId, String status, PageQuery query) {
        PageQuery safe = query.normalized();
        String normalizedStatus = normalizeStatus(status);
        long total = adoptions.count(userId, normalizedStatus);
        if (total == 0) {
            return PageResult.of(java.util.List.of(), safe.getPage(), safe.getSize(), 0);
        }
        var list = adoptions.findPage(userId, normalizedStatus, safe.offset(), safe.getSize())
                .stream()
                .map(record -> new AdoptionSummary(
                        record.adoptionNo(), record.userId(), record.userName(),
                        record.animalCode(), record.animalName(), record.planName(),
                        record.amount(), record.period(), record.status(), mask(record.contact()),
                        record.startDate(), record.endDate(), record.certificateNo(),
                        record.createdAt()))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), total);
    }

    private AdoptionDetailRecord require(String adoptionNo) {
        return adoptions.findDetail(adoptionNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("adoptionNo", adoptionNo)));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AdoptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "status"));
        }
    }

    private String mask(String contact) {
        if (contact == null || contact.isBlank()) {
            return contact;
        }
        String value = contact.trim();
        if (value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 7) {
            return value.charAt(0) + "****" + value.substring(value.length() - 2);
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
