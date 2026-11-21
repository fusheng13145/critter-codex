package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.HealthType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 健康与防疫应用服务。记录日期不得早于建档日，计划日期不得早于记录日期；
 * 提醒只做查询计算，不写消息表（消息通道归 C，避免跨域写表）。
 */
@Service
public class HealthService {

    private static final int MAX_RESULT_LENGTH = 255;
    private static final int REMINDER_DAYS = 3;

    private final HealthRecordMapper healthRecords;
    private final AnimalMapper animals;
    private final Clock clock;

    public HealthService(HealthRecordMapper healthRecords, AnimalMapper animals, Clock clock) {
        this.healthRecords = healthRecords;
        this.animals = animals;
        this.clock = clock;
    }

    @Transactional
    public CreatedHealthRecord create(String code, AddHealthRecordCommand command, Long operatorId) {
        LocalDateTime recordedAt = validate(command);
        Animal animal = findByCode(code);
        if (recordedAt.toLocalDate().isBefore(animal.getCreatedAt().toLocalDate())) {
            throw new BusinessException(30301, "记录日期不得早于建档日期");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        HealthRecord record = new HealthRecord();
        record.setAnimalId(animal.getId());
        record.setType(command.type());
        record.setRecordedAt(recordedAt);
        record.setResult(command.result().trim());
        record.setNextDate(command.nextDate());
        record.setOperatorId(operatorId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setCreatedBy(operatorId);
        healthRecords.insert(record);

        return new CreatedHealthRecord(record.getId());
    }

    public List<HealthRecordView> list(String code) {
        Animal animal = findByCode(code);
        List<HealthRecord> records = healthRecords.selectList(
                new LambdaQueryWrapper<HealthRecord>()
                        .eq(HealthRecord::getAnimalId, animal.getId())
                        .orderByDesc(HealthRecord::getRecordedAt)
                        .orderByDesc(HealthRecord::getId));

        LocalDate today = LocalDate.now(clock);
        return records.stream().map(record -> toView(record, today)).toList();
    }

    private LocalDateTime validate(AddHealthRecordCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.type() == null) {
            throw new BusinessException(30301, "健康记录类型必填");
        }
        if (command.recordedAt() == null) {
            throw new BusinessException(30301, "记录时间必填");
        }
        if (command.result() == null || command.result().isBlank()) {
            throw new BusinessException(30301, "记录结果必填");
        }
        String result = command.result().trim();
        if (result.length() > MAX_RESULT_LENGTH) {
            throw new BusinessException(30301, "记录结果不能超过 " + MAX_RESULT_LENGTH + " 字");
        }

        LocalDateTime recordedAt = command.recordedAt()
                .atZoneSameInstant(AppConfig.APP_ZONE)
                .toLocalDateTime();
        if (command.nextDate() != null && command.nextDate().isBefore(recordedAt.toLocalDate())) {
            throw new BusinessException(30301, "下次计划日期不得早于记录日期");
        }
        return recordedAt;
    }

    private Animal findByCode(String code) {
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return animal;
    }

    private HealthRecordView toView(HealthRecord record, LocalDate today) {
        Integer daysUntil = record.getNextDate() == null ? null
                : (int) ChronoUnit.DAYS.between(today, record.getNextDate());
        boolean reminderDue = record.getType() == HealthType.VACCINE
                && daysUntil != null && daysUntil <= REMINDER_DAYS;
        return new HealthRecordView(
                record.getId(),
                record.getType(),
                record.getType().label(),
                toOffset(record.getRecordedAt()),
                record.getResult(),
                record.getNextDate(),
                reminderDue,
                daysUntil,
                record.getOperatorId(),
                toOffset(record.getCreatedAt()));
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }
}
