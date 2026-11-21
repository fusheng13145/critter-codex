package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认养关系终止：仅 ACTIVE 可终止，结束后动物恢复在校园。
 */
@Service
public class AdoptionLifecycleService {

    private static final int MIN_REASON_LENGTH = 5;

    private final AdoptionRepository adoptions;
    private final AdoptableAnimalRepository animals;
    private final Clock clock;

    public AdoptionLifecycleService(AdoptionRepository adoptions,
                                    AdoptableAnimalRepository animals,
                                    Clock clock) {
        this.adoptions = adoptions;
        this.animals = animals;
        this.clock = clock;
    }

    @Transactional
    public TerminatedAdoption terminate(String adoptionNo, String reason) {
        String trimmedReason = trimToNull(reason);
        if (trimmedReason == null || trimmedReason.length() < MIN_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }

        AdoptionRecord record = adoptions.findByNoForUpdate(adoptionNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("adoptionNo", adoptionNo)));
        if (record.status() != AdoptionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID,
                    Map.of("adoptionNo", adoptionNo, "status", record.status().name()));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        int adoptionRows = adoptions.terminate(new AdoptionTermination(
                record.id(), trimmedReason, today, now));
        if (adoptionRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("adoptionNo", adoptionNo));
        }

        int animalRows = animals.markOnCampus(record.animalId());
        if (animalRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("animalCode", record.animalCode()));
        }

        return new TerminatedAdoption(adoptionNo, AdoptionStatus.TERMINATED, today, "ON_CAMPUS");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
