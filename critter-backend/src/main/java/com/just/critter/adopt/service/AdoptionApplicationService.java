package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.domain.AdoptionApplicationCommand;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * 认养申请应用服务。先锁定动物行，再在锁内串行检查生效关系与本人上限，
 * 最后才插入待审核单；方案金额和周期在提交时快照。
 */
@Service
public class AdoptionApplicationService {

    private static final int ACTIVE_LIMIT = 2;
    private static final Set<AnimalStatus> ADOPTABLE = Set.of(AnimalStatus.ON_CAMPUS, AnimalStatus.RELEASED);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AdoptionPlanRepository plans;
    private final AdoptableAnimalRepository animals;
    private final AdoptionRepository adoptions;
    private final SequencePort sequences;
    private final Clock clock;

    public AdoptionApplicationService(AdoptionPlanRepository plans,
                                      AdoptableAnimalRepository animals,
                                      AdoptionRepository adoptions,
                                      SequencePort sequences,
                                      Clock clock) {
        this.plans = plans;
        this.animals = animals;
        this.adoptions = adoptions;
        this.sequences = sequences;
        this.clock = clock;
    }

    @Transactional
    public CreatedAdoption create(AdoptionApplicationCommand command, long userId) {
        validate(command);
        String animalCode = command.animalCode().trim();

        AdoptionPlan plan = plans.findById(command.planId())
                .filter(AdoptionPlan::enabled)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("field", "planId")));

        AdoptableAnimal animal = animals.lockByCode(animalCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("animalCode", animalCode)));

        if (!ADOPTABLE.contains(animal.status())) {
            throw new BusinessException(ErrorCode.ANIMAL_NOT_ADOPTABLE,
                    Map.of("animalCode", animalCode, "status", animal.status().name()));
        }

        adoptions.findActiveAdoptionNo(animal.id()).ifPresent(activeNo -> {
            throw new BusinessException(ErrorCode.ANIMAL_ALREADY_ADOPTED,
                    Map.of("activeAdoptionNo", activeNo));
        });

        if (adoptions.countActiveByUser(userId) >= ACTIVE_LIMIT) {
            throw new BusinessException(ErrorCode.ADOPTION_LIMIT_EXCEEDED,
                    Map.of("limit", ACTIVE_LIMIT));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String adoptionNo = nextAdoptionNo(now.toLocalDate());
        AdoptionRecord record = new AdoptionRecord(
                0L,
                adoptionNo,
                userId,
                animal.id(),
                animalCode,
                plan.planId(),
                plan.amount(),
                plan.period(),
                AdoptionStatus.PENDING_AUDIT,
                command.contact().trim(),
                trimToNull(command.remark()),
                now);
        adoptions.save(record);

        return new CreatedAdoption(record.adoptionNo(), record.status(), record.amount(), record.period());
    }

    private String nextAdoptionNo(LocalDate today) {
        int sequence = sequences.next("ADOPTION", Year.now(clock).getValue());
        return "AD" + today.format(NO_DATE) + String.format("%03d", sequence);
    }

    private void validate(AdoptionApplicationCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.animalCode() == null || command.animalCode().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "animalCode"));
        }
        if (command.planId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "planId"));
        }
        if (!command.promiseAccepted()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "promiseAccepted"));
        }
        if (command.contact() == null || command.contact().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "contact"));
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
