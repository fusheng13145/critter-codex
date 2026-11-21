package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.domain.enums.AnimalEvent;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.HealthType;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.AnimalStatusLog;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.StateTransitionException;
import com.just.critter.domain.statemachine.Transition;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.AnimalStatusLogMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 动物状态迁移。目标状态先经表驱动状态机校验，再原子更新档案并写状态日志。
 */
@Service
public class AnimalStatusService {

    private static final int MIN_REASON_LENGTH = 5;

    private final AnimalMapper animals;
    private final AnimalStatusLogMapper statusLogs;
    private final HealthRecordMapper healthRecords;
    private final Clock clock;

    public AnimalStatusService(AnimalMapper animals, AnimalStatusLogMapper statusLogs,
                               HealthRecordMapper healthRecords, Clock clock) {
        this.animals = animals;
        this.statusLogs = statusLogs;
        this.healthRecords = healthRecords;
        this.clock = clock;
    }

    @Transactional
    public AnimalStatusResult change(String code, AnimalStatusChangeCommand command, long operatorId) {
        if (command == null || command.targetStatus() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标状态必填");
        }
        String reason = command.reason() == null ? "" : command.reason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED);
        }

        Animal animal = findByCode(code);
        AnimalStatus current = animal.getStatus();
        AnimalStatus target = command.targetStatus();
        List<AnimalStatus> allowed = StateMachines.animal().allowedTargets(current);
        AnimalEvent event = resolveEvent(current, target, allowed);

        TransitionContext context = TransitionContext.of(Role.ANIMAL_ADMIN,
                "surgeryRecorded", hasSurgeryRecord(animal.getId()));
        AnimalStatus next = StateMachines.animal().fire(current, event, context);

        LocalDateTime now = LocalDateTime.now(clock);
        animal.setStatus(next);
        animal.setStatusChangedAt(now);
        animal.setAdopted(next == AnimalStatus.ADOPTED);
        animal.setUpdatedAt(now);
        if (animals.updateById(animal) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }

        AnimalStatusLog log = new AnimalStatusLog();
        log.setAnimalId(animal.getId());
        log.setFromStatus(current.name());
        log.setToStatus(next.name());
        log.setEvent(event.name());
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setOccurredAt(now);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        log.setCreatedBy(operatorId);
        statusLogs.insert(log);

        return new AnimalStatusResult(animal.getCode(), next,
                StateMachines.animal().allowedTargets(next), toOffset(now));
    }

    public List<AnimalStatusLogView> history(String code) {
        Animal animal = findByCode(code);
        return statusLogs.selectList(new LambdaQueryWrapper<AnimalStatusLog>()
                        .eq(AnimalStatusLog::getAnimalId, animal.getId())
                        .orderByDesc(AnimalStatusLog::getOccurredAt)
                        .orderByDesc(AnimalStatusLog::getId))
                .stream()
                .map(log -> new AnimalStatusLogView(
                        AnimalStatus.valueOf(log.getFromStatus()), label(log.getFromStatus()),
                        AnimalStatus.valueOf(log.getToStatus()), label(log.getToStatus()),
                        AnimalEvent.valueOf(log.getEvent()), eventLabel(log.getEvent()),
                        log.getReason(), toOffset(log.getOccurredAt())))
                .toList();
    }

    private AnimalEvent resolveEvent(AnimalStatus current, AnimalStatus target, List<AnimalStatus> allowed) {
        List<Transition<AnimalStatus, AnimalEvent>> candidates = StateMachines.animal().transitions().stream()
                .filter(item -> item.from() == current && item.to() == target)
                .toList();
        if (candidates.isEmpty()) {
            throw new StateTransitionException(ErrorCode.STATE_TRANSITION_INVALID, current, target, null,
                    allowed, StateMachines.animal().allowedEvents(current));
        }
        return candidates.getFirst().event();
    }

    private boolean hasSurgeryRecord(Long animalId) {
        return healthRecords.selectCount(new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getAnimalId, animalId)
                .eq(HealthRecord::getType, HealthType.HISTORY)
                .and(wrapper -> wrapper.like(HealthRecord::getResult, "绝育")
                        .or().like(HealthRecord::getResult, "TNR"))) > 0;
    }

    private Animal findByCode(String code) {
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "动物编号不存在：" + code);
        }
        return animal;
    }

    private String label(String status) {
        return AnimalStatus.valueOf(status).label();
    }

    private String eventLabel(String event) {
        return AnimalEvent.valueOf(event).label();
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }
}
