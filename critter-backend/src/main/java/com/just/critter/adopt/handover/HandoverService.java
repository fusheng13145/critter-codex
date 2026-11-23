package com.just.critter.adopt.handover;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HandoverService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> STATUSES = Set.of("ACTIVE", "RETURNED", "CLOSED");
    private static final Set<String> RESULTS = Set.of("NORMAL", "ABNORMAL");
    private static final int FOLLOWUP_COUNT = 3;
    private static final int[] FOLLOWUP_DAYS = {7, 30, 90};

    private final HandoverRepository repository;
    private final SequencePort sequences;
    private final Clock clock;

    public HandoverService(HandoverRepository repository, SequencePort sequences, Clock clock) {
        this.repository = repository;
        this.sequences = sequences;
        this.clock = clock;
    }

    public PageResult<HandoverView> list(String status, PageQuery query) {
        String normalized = normalizeStatus(status);
        PageQuery page = query == null ? new PageQuery() : query.normalized();
        long total = repository.count(normalized);
        if (total == 0) {
            return PageResult.of(List.of(), page.getPage(), page.getSize(), 0);
        }
        return PageResult.of(repository.findPage(normalized, page.offset(), page.getSize()),
                page.getPage(), page.getSize(), total);
    }

    public HandoverDetail detail(String handoverNo) {
        HandoverView handover = repository.findByNo(handoverNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        return new HandoverDetail(handover, repository.findFollowups(handover.id()));
    }

    @Transactional
    public HandoverDetail create(HandoverCommand command, long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String animalCode = requireText(command.animalCode(), 20, "animalCode");
        String adopterName = requireText(command.adopterName(), 32, "adopterName");
        String contact = requireText(command.contact(), 32, "contact");
        if (!Boolean.TRUE.equals(command.guardianConsent())) {
            throw new BusinessException(ErrorCode.ADOPTION_MATERIAL_MISSING, Map.of("field", "guardianConsent"));
        }
        String residenceNote = command.residenceNote() == null ? "" : command.residenceNote().trim();
        if (residenceNote.length() < 5 || residenceNote.length() > 255) {
            throw new BusinessException(ErrorCode.ADOPTION_MATERIAL_MISSING, Map.of("field", "residenceNote"));
        }
        HandoverRepository.AnimalRef animal = repository.findAnimal(animalCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (repository.animalDeceased(animal.id())) {
            throw new BusinessException(ErrorCode.ANIMAL_NOT_ADOPTABLE);
        }

        LocalDateTime adoptedAt = command.adoptedAt() == null
                ? LocalDateTime.now(clock).withNano(0) : command.adoptedAt().withNano(0);
        LocalDate today = LocalDate.now(clock);
        int serial = sequences.next("HANDOVER" + DAY.format(today), today.getYear());
        String handoverNo = "HO" + DAY.format(today) + String.format("%03d", serial);
        long id = repository.insertHandover(handoverNo, animal.id(), animal.code(), command,
                adoptedAt, operatorId);
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        for (int i = 0; i < FOLLOWUP_COUNT; i++) {
            repository.insertFollowup(id, i + 1, adoptedAt.toLocalDate().plusDays(FOLLOWUP_DAYS[i]), operatorId);
        }
        return detail(handoverNo);
    }

    @Transactional
    public FollowupView complete(String handoverNo, int seqNo, FollowupCompleteCommand command, long operatorId) {
        HandoverView handover = repository.findByNo(handoverNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (seqNo < 1 || seqNo > FOLLOWUP_COUNT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "seqNo"));
        }
        String result = command == null ? null : command.result();
        if (result == null || !RESULTS.contains(result.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "result"));
        }
        String note = command.note() == null ? null : command.note().trim();
        if (note != null && note.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "note"));
        }
        FollowupView followup = repository.findFollowup(handover.id(), seqNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!"PENDING".equals(followup.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        int rows = repository.completeFollowup(followup.id(),
                result.trim().toUpperCase(Locale.ROOT), note,
                LocalDateTime.now(clock).withNano(0), operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return repository.findFollowup(handover.id(), seqNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "status"));
        }
        return value;
    }

    private String requireText(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
        }
        return text;
    }
}
