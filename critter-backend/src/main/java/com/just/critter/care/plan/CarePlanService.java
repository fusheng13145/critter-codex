package com.just.critter.care.plan;

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
public class CarePlanService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> TYPES = Set.of("VACCINE", "MEDICATION");
    private static final Set<String> STATUSES = Set.of("PLANNED", "DONE", "CANCELED");

    private final CarePlanRepository plans;
    private final SequencePort sequences;
    private final Clock clock;

    public CarePlanService(CarePlanRepository plans, SequencePort sequences, Clock clock) {
        this.plans = plans;
        this.sequences = sequences;
        this.clock = clock;
    }

    public PageResult<CarePlanView> list(String animalCode, String type, String status, PageQuery query) {
        String code = blankToNull(animalCode);
        String typeValue = normalizeEnum(type, TYPES, "type");
        String statusValue = normalizeEnum(status, STATUSES, "status");
        PageQuery page = query == null ? new PageQuery() : query.normalized();
        long total = plans.count(code, typeValue, statusValue);
        if (total == 0) {
            return PageResult.of(List.of(), page.getPage(), page.getSize(), 0);
        }
        return PageResult.of(plans.findPage(code, typeValue, statusValue, page.offset(), page.getSize()),
                page.getPage(), page.getSize(), total);
    }

    public List<CarePlanView> overdue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return plans.findOverdue(safeLimit);
    }

    @Transactional
    public CarePlanView create(CarePlanCommand command, long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String type = normalizeEnum(command.type(), TYPES, "type");
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "type"));
        }
        String itemName = requireText(command.itemName(), 64, "itemName");
        if (command.planDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "planDate"));
        }
        String animalCode = blankToNull(command.animalCode());
        CarePlanRepository.VisitRef visit = null;
        if (command.visitId() != null) {
            visit = plans.findVisit(command.visitId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND, "就诊记录不存在"));
            if (animalCode != null && !animalCode.equals(visit.animalCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "visitId"));
            }
            animalCode = visit.animalCode();
        }
        if (animalCode == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "animalCode"));
        }
        CarePlanRepository.AnimalRef animal = plans.findAnimal(animalCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        int serial = sequences.next("CAREPLAN" + DAY.format(today), today.getYear());
        String planNo = "CP" + DAY.format(today) + String.format("%03d", serial);
        long id = plans.insert(planNo, animal.id(), command, type,
                LocalDateTime.now(clock).withNano(0), operatorId);
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return plans.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public CarePlanView complete(long id, String result, String note, long operatorId) {
        CarePlanView plan = requirePlan(id);
        if (!"PLANNED".equals(plan.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        String resultText = requireText(result, 255, "result");
        String noteText = note == null ? null : note.trim();
        if (noteText != null && noteText.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "note"));
        }
        int rows = plans.complete(id, resultText, LocalDateTime.now(clock).withNano(0), operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return requirePlan(id);
    }

    @Transactional
    public CarePlanView cancel(long id, String note, long operatorId) {
        CarePlanView plan = requirePlan(id);
        if (!"PLANNED".equals(plan.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        String noteText = note == null ? null : note.trim();
        if (noteText != null && noteText.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "note"));
        }
        int rows = plans.cancel(id, noteText, LocalDateTime.now(clock).withNano(0), operatorId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return requirePlan(id);
    }

    private CarePlanView requirePlan(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "id"));
        }
        return plans.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    private String normalizeEnum(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", field));
        }
        return normalized;
    }

    private String requireText(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
