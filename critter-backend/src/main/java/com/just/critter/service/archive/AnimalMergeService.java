package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.AnimalAlias;
import com.just.critter.domain.model.AnimalChangeLog;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AdoptionMapper;
import com.just.critter.mapper.AnimalAliasMapper;
import com.just.critter.mapper.AnimalChangeLogMapper;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 重复档案合并。主档保留，副档只打 mergedIntoId，不物理删除也不逻辑删除：
 * 历史支出、认养、工单里的外键仍能取到原行；副编号写入 animal_alias，详情与搜索仍可命中主档。
 *
 * <p>有 ACTIVE 或 PENDING_PAYMENT 认养关系的档案禁止合并（30403），避免主档被“带入”
 * 一个已经生效或待缴费的认养关系。</p>
 */
@Service
public class AnimalMergeService {

    private static final int MIN_REASON = 5;
    private static final int MAX_SOURCES = 20;

    private final AnimalMapper animals;
    private final AnimalAliasMapper aliases;
    private final AdoptionMapper adoptions;
    private final HealthRecordMapper healthRecords;
    private final AnimalChangeLogMapper changeLogs;
    private final AuditLogService auditLog;
    private final Clock clock;

    public AnimalMergeService(AnimalMapper animals,
                              AnimalAliasMapper aliases,
                              AdoptionMapper adoptions,
                              HealthRecordMapper healthRecords,
                              AnimalChangeLogMapper changeLogs,
                              AuditLogService auditLog,
                              Clock clock) {
        this.animals = animals;
        this.aliases = aliases;
        this.adoptions = adoptions;
        this.healthRecords = healthRecords;
        this.changeLogs = changeLogs;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public AnimalMergeResult merge(AnimalMergeCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String mainCode = trimToNull(command.mainCode());
        if (mainCode == null) {
            throw new BusinessException(30301, "主档案编号必填");
        }
        String reason = trimToNull(command.reason());
        if (reason == null || reason.length() < MIN_REASON) {
            throw new BusinessException(30401, "合并原因至少 " + MIN_REASON + " 个字");
        }
        List<String> sourceCodes = normalizeSources(command.sourceCodes(), mainCode);
        if (sourceCodes.size() > MAX_SOURCES) {
            throw new BusinessException(30301, "单次最多合并 " + MAX_SOURCES + " 条副档案");
        }

        Animal main = requireAnimal(mainCode);
        if (main.getMergedIntoId() != null) {
            throw new BusinessException(30301, "主编号本身已是副编号");
        }
        requireNoBlockingAdoption(main.getId(), mainCode);

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<Animal> sources = new ArrayList<>();
        List<String> mergedCodes = new ArrayList<>();
        for (String code : sourceCodes) {
            Animal source = requireAnimal(code);
            if (Objects.equals(source.getId(), main.getId())) {
                throw new BusinessException(30301, "主编号不能同时作为副编号");
            }
            if (source.getMergedIntoId() != null) {
                throw new BusinessException(30301, "副编号已合并：" + code);
            }
            requireNoBlockingAdoption(source.getId(), code);
            sources.add(source);
            mergedCodes.add(source.getCode());
        }

        int movedHealth = 0;
        List<String> aliasCodes = new ArrayList<>();
        for (Animal source : sources) {
            aliasCodes.add(source.getCode());
            AnimalAlias alias = aliases.selectByAliasCode(source.getCode());
            if (alias != null && !Objects.equals(alias.getAnimalId(), main.getId())) {
                throw new BusinessException(ErrorCode.CODE_DUPLICATED, "副编号别名冲突：" + source.getCode());
            }
            if (alias == null) {
                AnimalAlias row = new AnimalAlias();
                row.setAliasCode(source.getCode());
                row.setAnimalId(main.getId());
                row.setSourceAnimalId(source.getId());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                row.setCreatedBy(operatorId);
                aliases.insert(row);
            }

            movedHealth += healthRecords.update(null, new LambdaUpdateWrapper<HealthRecord>()
                    .eq(HealthRecord::getAnimalId, source.getId())
                    .set(HealthRecord::getAnimalId, main.getId())
                    .set(HealthRecord::getUpdatedAt, now));
            changeLogs.update(null, new LambdaUpdateWrapper<AnimalChangeLog>()
                    .eq(AnimalChangeLog::getAnimalId, source.getId())
                    .set(AnimalChangeLog::getAnimalId, main.getId())
                    .set(AnimalChangeLog::getUpdatedAt, now));

            source.setMergedIntoId(main.getId());
            source.setMergedAt(now);
            source.setUpdatedAt(now);
            animals.updateById(source);
            addLog(source.getId(), "mergedInto", null, main.getCode(), reason, operatorId, now);
            addLog(main.getId(), "mergedFrom", null, source.getCode(), reason, operatorId, now);

            if (main.getName() == null && source.getName() != null) {
                main.setName(source.getName());
            }
            if (main.getCoverFileId() == null && source.getCoverFileId() != null) {
                main.setCoverFileId(source.getCoverFileId());
            }
            if (Boolean.TRUE.equals(source.getNeutered()) && !Boolean.TRUE.equals(main.getNeutered())) {
                main.setNeutered(true);
                main.setNeuteredAt(source.getNeuteredAt());
            }
        }
        main.setUpdatedAt(now);
        animals.updateById(main);

        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "ANIMAL_MERGE", "ANIMAL", main.getCode(),
                Map.of("mainCode", mainCode),
                Map.of("mergedCodes", mergedCodes, "aliases", aliasCodes, "movedHealthRecords", movedHealth));
        return new AnimalMergeResult(main.getCode(), mergedCodes, aliasCodes, movedHealth);
    }

    private List<String> normalizeSources(List<String> raw, String mainCode) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(30301, "请选择要并入的副编号");
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : raw) {
            String code = trimToNull(value);
            if (code == null) {
                throw new BusinessException(30301, "副编号不能为空");
            }
            if (code.equalsIgnoreCase(mainCode)) {
                throw new BusinessException(30301, "主编号不能同时作为副编号");
            }
            distinct.add(code);
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(30301, "请选择要并入的副编号");
        }
        return List.copyOf(distinct);
    }

    private Animal requireAnimal(String code) {
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "档案不存在：" + code);
        }
        return animal;
    }

    private void requireNoBlockingAdoption(Long animalId, String code) {
        if (adoptions.countBlockingByAnimalId(animalId) > 0) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS,
                    "档案存在生效或待缴费认养关系，不能合并：" + code);
        }
    }

    private void addLog(Long animalId, String field, String before, String after,
                        String reason, Long operatorId, LocalDateTime now) {
        AnimalChangeLog log = new AnimalChangeLog();
        log.setAnimalId(animalId);
        log.setField(field);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        log.setCreatedBy(operatorId);
        changeLogs.insert(log);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
