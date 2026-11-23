package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.model.AnimalGroup;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.PatrolRecord;
import com.just.critter.domain.model.Sighting;
import com.just.critter.mapper.AnimalGroupMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.PatrolRecordMapper;
import com.just.critter.mapper.SightingMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 巡查记录与“巡查转目击”。巡查本身只留痕；
 * 转目击时可选择目标群体（刷新最近目击并把点位并入群体），也可留作待归属。
 */
@Service
public class PatrolService {

    private static final int MAX_NOTE = 255;
    private static final int MAX_BEHAVIOR = 64;
    private static final int MAX_FILES = 6;

    private final PatrolRecordMapper patrols;
    private final BasePointMapper points;
    private final AnimalGroupMapper groups;
    private final SightingMapper sightings;
    private final AuditLogService auditLog;
    private final Clock clock;

    public PatrolService(PatrolRecordMapper patrols,
                         BasePointMapper points,
                         AnimalGroupMapper groups,
                         SightingMapper sightings,
                         AuditLogService auditLog,
                         Clock clock) {
        this.patrols = patrols;
        this.points = points;
        this.groups = groups;
        this.sightings = sightings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public PatrolView create(CreatePatrolCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        BasePoint point = requirePoint(command.pointId());
        if (command.patrolAt() == null) {
            throw new BusinessException(30301, "巡查时间必填");
        }
        if (command.countEstimate() != null && command.countEstimate() <= 0) {
            throw new BusinessException(30301, "巡查数量必须为正整数");
        }
        boolean hasAnomaly = Boolean.TRUE.equals(command.hasAnomaly());
        String anomalyNote = optionalText(command.anomalyNote(), MAX_NOTE, "异常说明最多 255 个字");
        if (hasAnomaly && anomalyNote == null) {
            throw new BusinessException(30301, "发现异常时请填写异常说明");
        }
        validateFiles(command.fileIds());

        LocalDateTime patrolAt = command.patrolAt().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        PatrolRecord record = new PatrolRecord();
        record.setPointId(point.getId());
        record.setPatrolAt(patrolAt);
        record.setCountEstimate(command.countEstimate());
        record.setHasAnomaly(hasAnomaly);
        record.setAnomalyNote(anomalyNote);
        record.setFileIds(command.fileIds() == null ? null : List.copyOf(command.fileIds()));
        record.setRecorderId(operatorId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setCreatedBy(operatorId);
        patrols.insert(record);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "PATROL_CREATE", "PATROL",
                String.valueOf(record.getId()), null, patrolView(record, point));
        return patrolView(record, point);
    }

    public PageResult<PatrolView> search(PatrolFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<PatrolRecord> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<PatrolRecord> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            if (filter.pointId() != null) {
                query.eq(PatrolRecord::getPointId, filter.pointId());
            }
            if (filter.from() != null) {
                query.ge(PatrolRecord::getPatrolAt, filter.from().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime());
            }
            if (filter.to() != null) {
                query.le(PatrolRecord::getPatrolAt, filter.to().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime());
            }
        }
        query.orderByDesc(PatrolRecord::getPatrolAt).orderByDesc(PatrolRecord::getId);
        IPage<PatrolRecord> result = patrols.selectPage(page, query);
        Set<Long> pointIds = new LinkedHashSet<>();
        result.getRecords().forEach(record -> pointIds.add(record.getPointId()));
        Map<Long, BasePoint> pointMap = pointIds.isEmpty() ? Map.of()
                : points.selectBatchIds(pointIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BasePoint::getId, p -> p, (a, b) -> a));
        List<PatrolView> rows = result.getRecords().stream()
                .map(record -> patrolView(record, pointMap.get(record.getPointId())))
                .toList();
        return PageResult.of(rows, safe.getPage(), safe.getSize(), result.getTotal());
    }

    /**
     * 巡查转目击。目标群体可空：空则生成 UNASSIGNED 待归属目击。
     */
    @Transactional
    public SightingView convert(Long id, ConvertPatrolCommand command, Long operatorId) {
        PatrolRecord patrol = id == null ? null : patrols.selectById(id);
        if (patrol == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "巡查记录不存在");
        }
        if (patrol.getConvertedSightingId() != null) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID, "该巡查已转过目击");
        }
        BasePoint point = requirePoint(patrol.getPointId());
        AnimalGroup target = resolveGroup(command == null ? null : command.groupCode());
        if (target != null && target.getStatus() == GroupStatus.GONE) {
            throw new BusinessException(30505, "已消失或已合并的群体不能承接目击");
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Sighting sighting = new Sighting();
        sighting.setGroupId(target == null ? null : target.getId());
        sighting.setPointId(point.getId());
        sighting.setSeenAt(patrol.getPatrolAt());
        sighting.setCountEstimate(patrol.getCountEstimate());
        sighting.setBehavior(behaviorOf(patrol));
        sighting.setFileIds(patrol.getFileIds() == null ? null : List.copyOf(patrol.getFileIds()));
        sighting.setRecorderId(operatorId);
        sighting.setAssignStatus(target == null ? "UNASSIGNED" : "ASSIGNED");
        sighting.setCreatedAt(now);
        sighting.setUpdatedAt(now);
        sighting.setCreatedBy(operatorId);
        sightings.insert(sighting);

        patrol.setConvertedSightingId(sighting.getId());
        patrol.setUpdatedAt(now);
        patrols.updateById(patrol);

        if (target != null) {
            List<Long> pointIds = target.getPointIds() == null ? new ArrayList<>()
                    : new ArrayList<>(target.getPointIds());
            if (!pointIds.contains(point.getId())) {
                pointIds.add(point.getId());
                target.setPointIds(pointIds);
            }
            if (patrol.getPatrolAt() != null
                    && (target.getLastSeenAt() == null || patrol.getPatrolAt().isAfter(target.getLastSeenAt()))) {
                target.setLastSeenAt(patrol.getPatrolAt());
            }
            target.setUpdatedAt(now);
            groups.updateById(target);
        }

        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "PATROL_CONVERT", "PATROL",
                String.valueOf(patrol.getId()),
                Map.of("pointId", point.getId(), "patrolAt", patrol.getPatrolAt()),
                Map.of("sightingId", sighting.getId(),
                        "groupCode", target == null ? "" : target.getGroupCode()));
        return toSightingView(sighting, target, point);
    }

    private AnimalGroup resolveGroup(String rawCode) {
        String code = rawCode == null ? null : rawCode.trim();
        if (code == null || code.isEmpty()) {
            return null;
        }
        AnimalGroup group = groups.selectByCode(code);
        if (group == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "目标群体不存在：" + code);
        }
        return group;
    }

    private BasePoint requirePoint(Long pointId) {
        if (pointId == null) {
            throw new BusinessException(30301, "巡查点位必填");
        }
        BasePoint point = points.selectById(pointId);
        if (point == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        return point;
    }

    private void validateFiles(List<String> fileIds) {
        if (fileIds == null) {
            return;
        }
        if (fileIds.size() > MAX_FILES) {
            throw new BusinessException(30301, "最多上传 " + MAX_FILES + " 张照片");
        }
        fileIds.forEach(fileId -> {
            if (fileId == null || fileId.isBlank()) {
                throw new BusinessException(30301, "照片 fileId 不能为空");
            }
        });
    }

    private String behaviorOf(PatrolRecord patrol) {
        String note = patrol.getAnomalyNote();
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_BEHAVIOR ? trimmed : trimmed.substring(0, MAX_BEHAVIOR);
    }

    private PatrolView patrolView(PatrolRecord record, BasePoint point) {
        return new PatrolView(
                record.getId(),
                point == null ? null : new PointRef(point.getId(), point.getPointName()),
                toOffset(record.getPatrolAt()),
                record.getCountEstimate(),
                Boolean.TRUE.equals(record.getHasAnomaly()),
                record.getAnomalyNote(),
                record.getFileIds() == null ? List.of() : List.copyOf(record.getFileIds()),
                record.getRecorderId(),
                record.getConvertedSightingId(),
                toOffset(record.getCreatedAt()));
    }

    private SightingView toSightingView(Sighting sighting, AnimalGroup group, BasePoint point) {
        return new SightingView(
                sighting.getId(),
                group == null ? null : group.getGroupCode(),
                point == null ? null : new PointRef(point.getId(), point.getPointName()),
                toOffset(sighting.getSeenAt()),
                sighting.getCountEstimate(),
                sighting.getBehavior(),
                sighting.getFileIds() == null ? List.of() : List.copyOf(sighting.getFileIds()),
                sighting.getRecorderId(),
                sighting.getAssignStatus(),
                toOffset(sighting.getCreatedAt()));
    }

    private String optionalText(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new BusinessException(30301, label);
        }
        return text;
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }

    @SuppressWarnings("unused")
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("unused")
    private boolean sameId(Long left, Long right) {
        return Objects.equals(left, right);
    }
}
