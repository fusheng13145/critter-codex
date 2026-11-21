package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.archive.GroupCodeGenerator;
import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.model.AnimalGroup;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.domain.model.Sighting;
import com.just.critter.mapper.AnimalGroupMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.SightingMapper;
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
 * 群体维护：合并、拆分与目击归属修正。群体编号是业务主键，合并后副群体只置 GONE，
 * 原始行与目击历史仍保留；拆分生成新编号，源群体保留并缩小规模区间。
 */
@Service
public class GroupMaintenanceService {

    private static final int MIN_REASON = 5;
    private static final int MAX_SOURCES = 20;

    private final AnimalGroupMapper groups;
    private final BaseSpeciesMapper species;
    private final BasePointMapper points;
    private final SightingMapper sightings;
    private final GroupCodeGenerator codeGenerator;
    private final AuditLogService auditLog;
    private final Clock clock;

    public GroupMaintenanceService(AnimalGroupMapper groups,
                                   BaseSpeciesMapper species,
                                   BasePointMapper points,
                                   SightingMapper sightings,
                                   GroupCodeGenerator codeGenerator,
                                   AuditLogService auditLog,
                                   Clock clock) {
        this.groups = groups;
        this.species = species;
        this.points = points;
        this.sightings = sightings;
        this.codeGenerator = codeGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public GroupMergeResult merge(GroupMergeCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String mainCode = trimToNull(command.mainCode());
        if (mainCode == null) {
            throw new BusinessException(30301, "主群体编号必填");
        }
        String reason = requireReason(command.reason());
        List<String> sourceCodes = normalizeSources(command.sourceCodes(), mainCode);

        AnimalGroup main = requireGroup(mainCode);
        if (main.getStatus() == GroupStatus.GONE) {
            throw new BusinessException(30505, "主群体已消失或已合并");
        }
        List<AnimalGroup> sourceGroups = new ArrayList<>();
        for (String code : sourceCodes) {
            AnimalGroup source = requireGroup(code);
            if (Objects.equals(source.getId(), main.getId())) {
                throw new BusinessException(30301, "主编号不能同时作为副编号");
            }
            if (source.getStatus() == GroupStatus.GONE || source.getMergedIntoCode() != null) {
                throw new BusinessException(30505, "副群体已消失或已合并：" + code);
            }
            if (!Objects.equals(source.getSpeciesId(), main.getSpeciesId())) {
                throw new BusinessException(30301, "只能合并同一物种的群体");
            }
            sourceGroups.add(source);
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Set<Long> pointIds = new LinkedHashSet<>();
        if (main.getPointIds() != null) {
            pointIds.addAll(main.getPointIds());
        }
        int min = main.getMinSize() == null ? 0 : main.getMinSize();
        int max = main.getMaxSize() == null ? 0 : main.getMaxSize();
        LocalDateTime lastSeen = main.getLastSeenAt();
        List<String> mergedFrom = new ArrayList<>();
        if (main.getMergedFrom() != null) {
            mergedFrom.addAll(main.getMergedFrom());
        }
        int movedSightings = 0;
        for (AnimalGroup source : sourceGroups) {
            if (source.getPointIds() != null) {
                pointIds.addAll(source.getPointIds());
            }
            min += source.getMinSize() == null ? 0 : source.getMinSize();
            max += source.getMaxSize() == null ? 0 : source.getMaxSize();
            if (source.getLastSeenAt() != null
                    && (lastSeen == null || source.getLastSeenAt().isAfter(lastSeen))) {
                lastSeen = source.getLastSeenAt();
            }
            if (!mergedFrom.contains(source.getGroupCode())) {
                mergedFrom.add(source.getGroupCode());
            }

            movedSightings += sightings.update(null, new LambdaUpdateWrapper<Sighting>()
                    .eq(Sighting::getGroupId, source.getId())
                    .set(Sighting::getGroupId, main.getId())
                    .set(Sighting::getUpdatedAt, now));

            source.setStatus(GroupStatus.GONE);
            source.setMergedIntoCode(main.getGroupCode());
            source.setUpdatedAt(now);
            groups.updateById(source);
        }

        main.setPointIds(List.copyOf(pointIds));
        main.setMinSize(min);
        main.setMaxSize(max);
        main.setLastSeenAt(lastSeen);
        main.setMergedFrom(mergedFrom);
        main.setUpdatedAt(now);
        groups.updateById(main);

        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "GROUP_MERGE", "GROUP", main.getGroupCode(),
                Map.of("mainCode", mainCode),
                Map.of("mergedCodes", sourceCodes, "minSize", min, "maxSize", max,
                        "movedSightings", movedSightings, "reason", reason));
        return new GroupMergeResult(main.getGroupCode(), sourceCodes, min, max,
                main.getPointIds(), movedSightings);
    }

    @Transactional
    public GroupSplitResult split(GroupSplitCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String sourceCode = trimToNull(command.sourceCode());
        if (sourceCode == null) {
            throw new BusinessException(30301, "源群体编号必填");
        }
        String reason = requireReason(command.reason());
        requireRange(command.newMinSize(), command.newMaxSize(), "新群体");
        requireRange(command.sourceMinSize(), command.sourceMaxSize(), "源群体");

        AnimalGroup source = requireGroup(sourceCode);
        if (source.getStatus() == GroupStatus.GONE) {
            throw new BusinessException(30505, "已消失或已合并的群体不能拆分");
        }
        List<Long> sourcePoints = source.getPointIds() == null ? List.of() : source.getPointIds();
        List<Long> newPoints = command.pointIds() == null || command.pointIds().isEmpty()
                ? sourcePoints : requirePointIds(command.pointIds());
        if (newPoints.isEmpty()) {
            throw new BusinessException(30301, "新群体至少保留一个活动点位");
        }
        String newFeatures = trimToNull(command.newFeatures());
        if (newFeatures == null) {
            newFeatures = source.getFeatures();
        }
        if (newFeatures == null || newFeatures.length() > 255) {
            throw new BusinessException(30301, "新群体识别特征不能为空且不超过 255 字");
        }
        String newBasis = trimToNull(command.newSizeBasis());
        if (newBasis == null || newBasis.length() > 64) {
            throw new BusinessException(30301, "新群体计数依据不能为空且不超过 64 字");
        }
        String sourceBasis = trimToNull(command.sourceSizeBasis());
        if (sourceBasis == null) {
            sourceBasis = source.getSizeBasis();
        }
        if (sourceBasis == null || sourceBasis.length() > 64) {
            throw new BusinessException(30301, "源群体计数依据不能为空且不超过 64 字");
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        AnimalGroup split = new AnimalGroup();
        split.setGroupCode(codeGenerator.next());
        split.setSpeciesId(source.getSpeciesId());
        split.setFeatures(newFeatures);
        split.setPointIds(newPoints);
        split.setMinSize(command.newMinSize());
        split.setMaxSize(command.newMaxSize());
        split.setSizeBasis(newBasis);
        split.setStatus(GroupStatus.ACTIVE);
        split.setLastSeenAt(source.getLastSeenAt());
        split.setSplitFrom(source.getGroupCode());
        split.setCreatedAt(now);
        split.setUpdatedAt(now);
        split.setCreatedBy(operatorId);
        groups.insert(split);

        source.setMinSize(command.sourceMinSize());
        source.setMaxSize(command.sourceMaxSize());
        source.setSizeBasis(sourceBasis);
        source.setUpdatedAt(now);
        groups.updateById(source);

        int moved = moveSightings(command.moveSightingIds(), source.getId(), split.getId(), now);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "GROUP_SPLIT", "GROUP", source.getGroupCode(),
                Map.of("sourceCode", source.getGroupCode(), "newCode", split.getGroupCode()),
                Map.of("sourceRange", command.sourceMinSize() + "-" + command.sourceMaxSize(),
                        "newRange", command.newMinSize() + "-" + command.newMaxSize(),
                        "movedSightings", moved, "reason", reason));
        return new GroupSplitResult(source.getGroupCode(), split.getGroupCode(),
                command.sourceMinSize(), command.sourceMaxSize(),
                command.newMinSize(), command.newMaxSize(), moved);
    }

    @Transactional
    public SightingView assignSighting(Long sightingId, SightingAssignmentCommand command, Long operatorId) {
        if (sightingId == null) {
            throw new BusinessException(30301, "目击记录编号必填");
        }
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        Sighting sighting = sightings.selectById(sightingId);
        if (sighting == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "目击记录不存在");
        }
        boolean pending = Boolean.TRUE.equals(command.pending());
        if (pending && (command.groupId() != null || trimToNull(command.groupCode()) != null)) {
            throw new BusinessException(30301, "标记待归属时不能同时指定群体");
        }

        AnimalGroup target = null;
        if (pending) {
            sighting.setGroupId(null);
            sighting.setAssignStatus("UNASSIGNED");
        } else {
            target = resolveGroup(command);
            if (target.getStatus() == GroupStatus.GONE) {
                throw new BusinessException(30505, "已消失或已合并的群体不能承接目击");
            }
            sighting.setGroupId(target.getId());
            sighting.setAssignStatus("ASSIGNED");
        }
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        sighting.setUpdatedAt(now);
        sightings.updateById(sighting);

        if (target != null && sighting.getSeenAt() != null
                && (target.getLastSeenAt() == null || sighting.getSeenAt().isAfter(target.getLastSeenAt()))) {
            target.setLastSeenAt(sighting.getSeenAt());
            target.setUpdatedAt(now);
            groups.updateById(target);
        }

        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "SIGHTING_ASSIGN", "SIGHTING",
                String.valueOf(sightingId),
                Map.of("assignStatus", sighting.getAssignStatus()),
                Map.of("groupCode", target == null ? "" : target.getGroupCode(),
                        "reason", dataOrEmpty(command.reason())));
        return toView(sighting, target);
    }

    private int moveSightings(List<Long> ids, Long sourceGroupId, Long newGroupId, LocalDateTime now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        int moved = 0;
        for (Long id : distinct) {
            if (id == null) {
                throw new BusinessException(30301, "目击记录编号不能为空");
            }
            Sighting row = sightings.selectById(id);
            if (row == null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "目击记录不存在：" + id);
            }
            if (!Objects.equals(row.getGroupId(), sourceGroupId)) {
                throw new BusinessException(30301, "目击记录不属于源群体：" + id);
            }
            row.setGroupId(newGroupId);
            row.setUpdatedAt(now);
            sightings.updateById(row);
            moved++;
        }
        return moved;
    }

    private AnimalGroup resolveGroup(SightingAssignmentCommand command) {
        AnimalGroup group = null;
        if (command.groupId() != null) {
            group = groups.selectById(command.groupId());
        }
        String code = trimToNull(command.groupCode());
        if (group == null && code != null) {
            group = groups.selectByCode(code);
        }
        if (group == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "目标群体不存在");
        }
        return group;
    }

    private SightingView toView(Sighting sighting, AnimalGroup group) {
        BasePoint point = sighting.getPointId() == null ? null : points.selectById(sighting.getPointId());
        return new SightingView(
                sighting.getId(),
                group == null ? null : group.getGroupCode(),
                point == null ? null : new PointRef(point.getId(), point.getPointName()),
                toOffset(sighting.getSeenAt()),
                sighting.getCountEstimate(),
                sighting.getBehavior(),
                sighting.getFileIds(),
                sighting.getRecorderId(),
                sighting.getAssignStatus(),
                toOffset(sighting.getCreatedAt()));
    }

    private List<String> normalizeSources(List<String> raw, String mainCode) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(30301, "请选择要并入的副群体");
        }
        Set<String> distinct = new LinkedHashSet<>();
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
        if (distinct.size() > MAX_SOURCES) {
            throw new BusinessException(30301, "单次最多合并 " + MAX_SOURCES + " 个副群体");
        }
        return List.copyOf(distinct);
    }

    private AnimalGroup requireGroup(String code) {
        AnimalGroup group = groups.selectByCode(code);
        if (group == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "群体不存在：" + code);
        }
        return group;
    }

    private List<Long> requirePointIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(30301, "至少选择一个活动点位");
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id == null) {
                throw new BusinessException(30301, "点位 id 不能为空");
            }
            distinct.add(id);
        }
        List<Long> ids = List.copyOf(distinct);
        List<BasePoint> found = points.selectBatchIds(ids);
        if (found.size() != ids.size()) {
            throw new BusinessException(30410, "点位不存在");
        }
        return ids;
    }

    private void requireRange(Integer min, Integer max, String label) {
        if (min == null || max == null || min < 1 || max < min) {
            throw new BusinessException(ErrorCode.SIZE_RANGE_INVALID, label + "规模区间非法");
        }
    }

    private String requireReason(String value) {
        String reason = trimToNull(value);
        if (reason == null || reason.length() < MIN_REASON) {
            throw new BusinessException(30401, "请填写变更原因，且不少于 " + MIN_REASON + " 个字");
        }
        return reason;
    }

    private String dataOrEmpty(String value) {
        String text = trimToNull(value);
        return text == null ? "" : text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }
}
