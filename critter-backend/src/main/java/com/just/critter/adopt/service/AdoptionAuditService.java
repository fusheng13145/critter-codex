package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageType;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 认养审核服务。审核通过只把申请转为待缴费，绝不在此步写入 ACTIVE；
 * 动物行锁与关系占用检查保证同一动物不会被两张单同时占住。
 */
@Service
public class AdoptionAuditService {

    private static final int PAYMENT_DAYS = 7;
    private static final int MIN_REASON_LENGTH = 5;
    private static final Set<AnimalStatus> ADOPTABLE = Set.of(AnimalStatus.ON_CAMPUS, AnimalStatus.RELEASED);

    private final AdoptionRepository adoptions;
    private final AdoptableAnimalRepository animals;
    private final MessageRepository messages;
    private final Clock clock;

    public AdoptionAuditService(AdoptionRepository adoptions,
                                AdoptableAnimalRepository animals,
                                MessageRepository messages,
                                Clock clock) {
        this.adoptions = adoptions;
        this.animals = animals;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public AuditedAdoption audit(String adoptionNo, AdoptionAuditCommand command, long auditorId) {
        validate(command);

        AdoptionRecord record = adoptions.findByNoForUpdate(adoptionNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("adoptionNo", adoptionNo)));

        if (record.status() != AdoptionStatus.PENDING_AUDIT) {
            throw new BusinessException(ErrorCode.AUDIT_CONFLICT,
                    Map.of("adoptionNo", adoptionNo, "status", record.status().name()));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String reason = trimToNull(command.reason());

        if (command.decision() == AdoptionDecision.REJECT) {
            int updated = adoptions.updateAudit(adoptionNo, AdoptionStatus.REJECTED,
                    auditorId, reason, record.amount(), now);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.AUDIT_CONFLICT, Map.of("adoptionNo", adoptionNo));
            }
            notifyApplicant(record, "认养申请未通过：" + reason, auditorId, now);
            return new AuditedAdoption(adoptionNo, AdoptionStatus.REJECTED, null);
        }

        int amount = command.amount() == null ? record.amount() : command.amount();

        AdoptableAnimal animal = animals.lockByCode(record.animalCode())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("animalCode", record.animalCode())));
        if (!ADOPTABLE.contains(animal.status())) {
            throw new BusinessException(ErrorCode.ANIMAL_NOT_ADOPTABLE,
                    Map.of("animalCode", record.animalCode(), "status", animal.status().name()));
        }

        adoptions.findBlockingAdoptionNo(record.animalId(), adoptionNo).ifPresent(blockingNo -> {
            throw new BusinessException(ErrorCode.AUDIT_CONFLICT,
                    Map.of("activeAdoptionNo", blockingNo));
        });

        int updated = adoptions.updateAudit(adoptionNo, AdoptionStatus.PENDING_PAYMENT,
                auditorId, reason, amount, now);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.AUDIT_CONFLICT, Map.of("adoptionNo", adoptionNo));
        }
        notifyApplicant(record, "认养申请已通过，请在7日内完成缴费", auditorId, now);

        return new AuditedAdoption(adoptionNo, AdoptionStatus.PENDING_PAYMENT,
                now.plusDays(PAYMENT_DAYS).atZone(clock.getZone()).toOffsetDateTime());
    }

    private void notifyApplicant(AdoptionRecord record, String title, long auditorId, LocalDateTime now) {
        int inserted = messages.insert(new MessageWrite(
                record.userId(), MessageType.APPROVAL_RESULT.name(), title,
                "ADOPTION", record.adoptionNo(), auditorId, now));
        if (inserted != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("adoptionNo", record.adoptionNo()));
        }
    }

    private void validate(AdoptionAuditCommand command) {
        if (command == null || command.decision() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "decision"));
        }
        if (command.decision() == AdoptionDecision.APPROVE) {
            if (command.amount() != null && command.amount() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "amount"));
            }
            if (command.amount() != null && !hasReason(command.reason())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
            }
            return;
        }
        if (!hasReason(command.reason())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
    }

    private boolean hasReason(String reason) {
        String trimmed = trimToNull(reason);
        return trimmed != null && trimmed.length() >= MIN_REASON_LENGTH;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
