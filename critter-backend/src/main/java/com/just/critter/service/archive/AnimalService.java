package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.archive.AnimalCodeGenerator;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Gender;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.AnimalChangeLog;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.mapper.AnimalChangeLogMapper;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 档案应用服务：建档发号、五条件检索、详情裁剪、编辑留痕。
 * 状态迁移不在这里做，统一走状态机；本类只改普通字段。
 */
@Service
public class AnimalService {

    private static final int MIN_FEATURES_LENGTH = 5;
    private static final int MIN_REASON_LENGTH = 5;
    private static final int MAX_PHOTOS = 6;
    private static final int MIN_AGE = 0;
    private static final int MAX_AGE = 25;

    private final AnimalMapper animals;
    private final BasePointMapper points;
    private final AnimalChangeLogMapper changeLogs;
    private final AnimalCodeGenerator codeGenerator;
    private final Clock clock;

    public AnimalService(AnimalMapper animals,
                         BasePointMapper points,
                         AnimalChangeLogMapper changeLogs,
                         AnimalCodeGenerator codeGenerator,
                         Clock clock) {
        this.animals = animals;
        this.points = points;
        this.changeLogs = changeLogs;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreatedAnimal create(CreateAnimalCommand command, Long operatorId) {
        validateCreate(command);
        BasePoint point = requirePoint(command.pointId());

        LocalDateTime now = LocalDateTime.now(clock);
        Animal animal = new Animal();
        animal.setCode(codeGenerator.next(command.category()));
        animal.setCategory(command.category());
        animal.setGender(command.gender());
        animal.setColor(command.color().trim());
        animal.setEstimatedAge(command.estimatedAge());
        animal.setFeatures(command.features().trim());
        animal.setPointId(point.getId());
        animal.setStatus(AnimalStatus.ON_CAMPUS);
        animal.setNeutered(false);
        animal.setAdopted(false);
        animal.setCoverFileId(command.photos().getFirst());
        animal.setInternalNote(trimToNull(command.healthNote()));
        animal.setDeleted(0);
        animal.setVersion(0);
        animal.setCreatedAt(now);
        animal.setUpdatedAt(now);
        animal.setCreatedBy(operatorId);

        animals.insert(animal);
        return new CreatedAnimal(animal.getCode(), animal.getStatus(), toOffset(now));
    }

    public PageResult<AnimalSummary> search(AnimalFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Animal> page = new Page<>(safe.getPage(), safe.getSize());

        LambdaQueryWrapper<Animal> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            String keyword = trimToNull(filter.keyword());
            if (keyword != null) {
                query.and(w -> w.like(Animal::getCode, keyword).or().like(Animal::getFeatures, keyword));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                query.in(Animal::getStatus, filter.statuses());
            }
            if (filter.pointId() != null) {
                query.eq(Animal::getPointId, filter.pointId());
            }
            if (filter.neutered() != null) {
                query.eq(Animal::getNeutered, filter.neutered());
            }
            if (filter.adopted() != null) {
                query.eq(Animal::getAdopted, filter.adopted());
            }
        }
        query.orderByDesc(Animal::getUpdatedAt);

        IPage<Animal> result = animals.selectPage(page, query);
        Map<Long, BasePoint> pointMap = loadPoints(result.getRecords());
        List<AnimalSummary> list = result.getRecords().stream()
                .map(animal -> toSummary(animal, pointMap.get(animal.getPointId())))
                .toList();

        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    public AnimalDetail detail(String code, boolean includeInternalNote) {
        Animal animal = findByCode(code);
        return toDetail(animal, includeInternalNote, loadPoint(animal.getPointId()));
    }

    @Transactional
    public AnimalDetail update(String code, UpdateAnimalCommand command, Long operatorId) {
        if (command == null || command.reason() == null || command.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new BusinessException(30401, "请填写变更原因，且不少于 5 个字");
        }
        Animal animal = findByCode(code);
        String reason = command.reason().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        List<FieldChange> changes = new ArrayList<>();

        if (command.name() != null) {
            String next = trimToNull(command.name());
            addChange(changes, "name", animal.getName(), next);
            animal.setName(next);
        }
        if (command.gender() != null) {
            addChange(changes, "gender", animal.getGender(), command.gender());
            animal.setGender(command.gender());
        }
        if (command.color() != null) {
            String next = requireText(command.color(), "花色不能为空");
            addChange(changes, "color", animal.getColor(), next);
            animal.setColor(next);
        }
        if (command.estimatedAge() != null) {
            requireAge(command.estimatedAge());
            addChange(changes, "estimatedAge", animal.getEstimatedAge(), command.estimatedAge());
            animal.setEstimatedAge(command.estimatedAge());
        }
        if (command.features() != null) {
            String next = requireFeatures(command.features());
            addChange(changes, "features", animal.getFeatures(), next);
            animal.setFeatures(next);
        }
        if (command.pointId() != null) {
            BasePoint point = requirePoint(command.pointId());
            addChange(changes, "pointId", animal.getPointId(), point.getId());
            animal.setPointId(point.getId());
        }
        if (command.internalNote() != null) {
            String next = trimToNull(command.internalNote());
            addChange(changes, "internalNote", animal.getInternalNote(), next);
            animal.setInternalNote(next);
        }

        if (changes.isEmpty()) {
            return toDetail(animal, true, loadPoint(animal.getPointId()));
        }

        animal.setUpdatedAt(now);
        int updated = animals.updateById(animal);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        for (FieldChange change : changes) {
            AnimalChangeLog log = new AnimalChangeLog();
            log.setAnimalId(animal.getId());
            log.setField(change.field());
            log.setBeforeValue(change.before());
            log.setAfterValue(change.after());
            log.setReason(reason);
            log.setOperatorId(operatorId);
            log.setCreatedAt(now);
            log.setUpdatedAt(now);
            log.setCreatedBy(operatorId);
            changeLogs.insert(log);
        }
        return toDetail(animal, true, loadPoint(animal.getPointId()));
    }

    public List<AnimalChangeLogView> history(String code) {
        Animal animal = findByCode(code);
        List<AnimalChangeLog> logs = changeLogs.selectList(
                new LambdaQueryWrapper<AnimalChangeLog>()
                        .eq(AnimalChangeLog::getAnimalId, animal.getId())
                        .orderByDesc(AnimalChangeLog::getCreatedAt)
                        .orderByDesc(AnimalChangeLog::getId));
        return logs.stream()
                .map(log -> new AnimalChangeLogView(log.getField(), log.getBeforeValue(), log.getAfterValue(),
                        log.getReason(), log.getOperatorId(), toOffset(log.getCreatedAt())))
                .toList();
    }

    private void validateCreate(CreateAnimalCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.clientCode() != null && !command.clientCode().isBlank()) {
            throw new BusinessException(ErrorCode.CODE_DUPLICATED);
        }
        if (command.category() == null) {
            throw new BusinessException(30301, "动物类别必填");
        }
        if (command.gender() == null) {
            throw new BusinessException(30303, "性别取值非法");
        }
        requireText(command.color(), "花色不能为空");
        requireFeatures(command.features());
        requireAge(command.estimatedAge());
        if (command.pointId() == null) {
            throw new BusinessException(30301, "常驻点位必填");
        }
        if (command.photos() == null || command.photos().isEmpty()) {
            throw new BusinessException(30301, "请至少上传 1 张照片");
        }
        if (command.photos().size() > MAX_PHOTOS) {
            throw new BusinessException(ErrorCode.FILE_INVALID, Map.of("maxPhotos", MAX_PHOTOS));
        }
        command.photos().forEach(photo -> {
            if (photo == null || photo.isBlank()) {
                throw new BusinessException(30301, "照片 fileId 不能为空");
            }
        });
    }

    private String requireFeatures(String features) {
        String value = trimToNull(features);
        if (value == null || value.length() < MIN_FEATURES_LENGTH) {
            throw new BusinessException(30301, "识别特征至少 5 个字");
        }
        return value;
    }

    private String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(30301, message);
        }
        return text;
    }

    private void requireAge(Integer age) {
        if (age == null) {
            return;
        }
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BusinessException(30301, "估算年龄需在 0—25 之间");
        }
    }

    private BasePoint requirePoint(Long pointId) {
        BasePoint point = points.selectById(pointId);
        if (point == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        return point;
    }

    private Animal findByCode(String code) {
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return animal;
    }

    private BasePoint loadPoint(Long pointId) {
        return pointId == null ? null : points.selectById(pointId);
    }

    private Map<Long, BasePoint> loadPoints(Collection<Animal> records) {
        Set<Long> ids = records.stream().map(Animal::getPointId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BasePoint> map = new HashMap<>();
        points.selectBatchIds(ids).forEach(point -> map.put(point.getId(), point));
        return map;
    }

    private AnimalSummary toSummary(Animal animal, BasePoint point) {
        return new AnimalSummary(
                animal.getCode(),
                animal.getCategory(),
                animal.getName(),
                animal.getGender() == null ? null : animal.getGender().name(),
                animal.getColor(),
                animal.getEstimatedAge(),
                point == null ? null : new PointRef(point.getId(), point.getPointName()),
                animal.getStatus(),
                Boolean.TRUE.equals(animal.getNeutered()),
                Boolean.TRUE.equals(animal.getAdopted()),
                coverUrl(animal.getCoverFileId()),
                toOffset(animal.getUpdatedAt()));
    }

    private AnimalDetail toDetail(Animal animal, boolean includeInternalNote, BasePoint point) {
        return new AnimalDetail(
                animal.getCode(),
                animal.getCategory(),
                animal.getName(),
                animal.getGender() == null ? null : animal.getGender().name(),
                animal.getColor(),
                animal.getEstimatedAge(),
                animal.getFeatures(),
                point == null ? null : new PointRef(point.getId(), point.getPointName()),
                animal.getStatus(),
                animal.getStatus() == null ? List.of() : StateMachines.animal().allowedTargets(animal.getStatus()),
                Boolean.TRUE.equals(animal.getNeutered()),
                animal.getNeuteredAt(),
                Boolean.TRUE.equals(animal.getAdopted()),
                coverUrl(animal.getCoverFileId()),
                includeInternalNote ? animal.getInternalNote() : null,
                toOffset(animal.getCreatedAt()),
                toOffset(animal.getUpdatedAt()));
    }

    private String coverUrl(String coverFileId) {
        return coverFileId == null || coverFileId.isBlank() ? null : "/api/v1/files/" + coverFileId;
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addChange(List<FieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, stringify(before), stringify(after)));
        }
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record FieldChange(String field, String before, String after) {
    }
}
