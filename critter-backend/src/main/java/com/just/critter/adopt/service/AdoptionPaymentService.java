package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.domain.AdoptionPaymentCommand;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageType;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缴费入账：只有本动作能把认养单从 PENDING_PAYMENT 转 ACTIVE。
 * 写缴费、改状态、改动物标记必须同事务。
 */
@Service
public class AdoptionPaymentService {

    private static final Set<AnimalStatus> ADOPTABLE = Set.of(AnimalStatus.ON_CAMPUS, AnimalStatus.RELEASED);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AdoptionRepository adoptions;
    private final AdoptableAnimalRepository animals;
    private final SequencePort sequences;
    private final MessageRepository messages;
    private final Clock clock;

    public AdoptionPaymentService(AdoptionRepository adoptions,
                                  AdoptableAnimalRepository animals,
                                  SequencePort sequences,
                                  MessageRepository messages,
                                  Clock clock) {
        this.adoptions = adoptions;
        this.animals = animals;
        this.sequences = sequences;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public AdoptionPaymentResult pay(String adoptionNo, AdoptionPaymentCommand command, long verifierId) {
        validate(command);

        AdoptionRecord record = adoptions.findByNoForUpdate(adoptionNo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("adoptionNo", adoptionNo)));
        if (record.status() != AdoptionStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID,
                    Map.of("adoptionNo", adoptionNo, "status", record.status().name()));
        }
        if (adoptions.hasPayment(record.id())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID,
                    Map.of("adoptionNo", adoptionNo, "reason", "已存在缴费流水"));
        }

        AdoptableAnimal animal = animals.lockByCode(record.animalCode())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORD_NOT_FOUND, Map.of("animalCode", record.animalCode())));
        if (!ADOPTABLE.contains(animal.status())) {
            throw new BusinessException(ErrorCode.ANIMAL_NOT_ADOPTABLE,
                    Map.of("animalCode", record.animalCode(), "status", animal.status().name()));
        }

        String note = trimToNull(command.note());
        boolean amountMismatch = command.paidAmount() != record.amount();
        if (amountMismatch && note == null) {
            throw new BusinessException(ErrorCode.PAYMENT_MISMATCH,
                    Map.of("expected", record.amount(), "paid", command.paidAmount()));
        }

        LocalDateTime paidAt = LocalDateTime.ofInstant(command.paidAt().toInstant(), clock.getZone());
        LocalDate startDate = paidAt.toLocalDate();
        LocalDate endDate = startDate.plusMonths(record.period().months());
        int year = Year.now(clock).getValue();
        String paymentNo = "PAY" + startDate.format(NO_DATE)
                + String.format("%03d", sequences.next("ADOPTION_PAYMENT", year));
        String certificateNo = "QYWL-AD-" + year + "-"
                + String.format("%04d", sequences.next("ADOPTION_CERTIFICATE", year));
        LocalDateTime now = LocalDateTime.now(clock);

        int paymentRows = adoptions.insertPayment(new AdoptionPaymentWrite(
                paymentNo, record.id(), command.paidAmount(), paidAt, verifierId, now,
                command.voucherFileIds(), note));
        if (paymentRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }

        int animalRows = animals.markAdopted(animal.id());
        if (animalRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("animalCode", record.animalCode()));
        }

        int adoptionRows = adoptions.activate(new AdoptionActivation(
                record.id(), certificateNo, startDate, endDate, now));
        if (adoptionRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("adoptionNo", adoptionNo));
        }

        int messageRows = messages.insert(new MessageWrite(
                record.userId(), MessageType.APPROVAL_RESULT.name(),
                "认养缴费已入账，认养关系已生效，证书号 " + certificateNo,
                "ADOPTION", adoptionNo, verifierId, now));
        if (messageRows != 1) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT,
                    Map.of("adoptionNo", adoptionNo));
        }

        return new AdoptionPaymentResult(adoptionNo, AdoptionStatus.ACTIVE,
                startDate, endDate, certificateNo, "ADOPTED");
    }

    private void validate(AdoptionPaymentCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.paidAmount() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "paidAmount"));
        }
        if (command.paidAt() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "paidAt"));
        }
        if (command.paidAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "paidAt"));
        }
        List<String> vouchers = command.voucherFileIds();
        if (vouchers == null || vouchers.isEmpty()
                || vouchers.stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "voucherFileIds"));
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
