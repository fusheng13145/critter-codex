package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 群体与目击应用服务。群体不建个体、不做医疗，状态迁移只走状态机；
 * 目击只刷新 last_seen_at，是否从"已消失"回到"活跃"由管理员确认（后续迭代）。
 */
@Service
public class GroupService {

    private static final int MAX_FEATURES = 255;
    private static final int MAX_BASIS = 64;
    private static final int MAX_BEHAVIOR = 64;

    private final AnimalGroupMapper groups;
    private final BaseSpeciesMapper species;
    private final BasePointMapper points;
    private final SightingMapper sightings;
    private final GroupCodeGenerator codeGenerator;
    private final Clock clock;

    public GroupService(AnimalGroupMapper groups,
                        BaseSpeciesMapper species,
                        BasePointMapper points,
                        SightingMapper sightings,
                        GroupCodeGenerator codeGenerator,
                        Clock clock) {
        this.groups = groups;
        this.species = species;
        this.points = points;
        this.sightings = sightings;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreatedGroup create(CreateGroupCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        requireRange(command.minSize(), command.maxSize());
        String features = requireText(command.features(), MAX_FEATURES, "识别特征不能为空");
        String basis = requireText(command.sizeBasis(), MAX_BASIS, "计数依据不能为空");
        BaseSpecies speciesRow = requireSpecies(command.speciesId());
        List<Long> pointIds = requirePointIds(command.pointIds());

        LocalDateTime now = LocalDateTime.now(clock);
        AnimalGroup group = new AnimalGroup();
        group.setGroupCode(codeGenerator.next());
        group.setSpeciesId(speciesRow.getId());
        group.setFeatures(features);
        group.setPointIds(pointIds);
        group.setMinSize(command.minSize());
        group.setMaxSize(command.maxSize());
        group.setSizeBasis(basis);
        group.setStatus(GroupStatus.ACTIVE);
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group.setCreatedBy(operatorId);
        groups.insert(group);

        return new CreatedGroup(group.getGroupCode(), group.getStatus());
    }

    public PageResult<GroupSummary> search(GroupFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<AnimalGroup> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<AnimalGroup> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            String keyword = trimToNull(filter.keyword());
            if (keyword != null) {
                query.and(w -> w.like(AnimalGroup::getGroupCode, keyword)
                        .or().like(AnimalGroup::getFeatures, keyword));
            }
            if (filter.speciesId() != null) {
                query.eq(AnimalGroup::getSpeciesId, filter.speciesId());
            }
            if (filter.status() != null) {
                query.eq(AnimalGroup::getStatus, filter.status());
            }
            if (filter.pointId() != null) {
                query.apply("JSON_CONTAINS(point_ids, {0})", filter.pointId());
            }
        }
        query.orderByDesc(AnimalGroup::getUpdatedAt)
                .orderByDesc(AnimalGroup::getId);

        IPage<AnimalGroup> result = groups.selectPage(page, query);
        Map<Long, BasePoint> pointMap = loadPoints(result.getRecords().stream()
                .map(AnimalGroup::getPointIds).filter(Objects::nonNull).flatMap(Collection::stream).toList());
        Map<Long, BaseSpecies> speciesMap = new HashMap<>();
        List<GroupSummary> rows = result.getRecords().stream()
                .map(group -> {
                    BaseSpecies speciesRow = speciesMap.computeIfAbsent(group.getSpeciesId(), species::selectById);
                    return toSummary(group, speciesRow, pointMap);
                })
                .toList();
        return PageResult.of(rows, safe.getPage(), safe.getSize(), result.getTotal());
    }

    public GroupDetail detail(String code) {
        AnimalGroup group = findByCode(code);
        return toDetail(group, code.trim(), species.selectById(group.getSpeciesId()),
                loadPoints(group.getPointIds()));
    }

    @Transactional
    public GroupDetail update(String code, UpdateGroupCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        AnimalGroup group = findByCode(code);
        requireNotGone(group);
        int lower = command.minSize() == null ? group.getMinSize() : command.minSize();
        int upper = command.maxSize() == null ? group.getMaxSize() : command.maxSize();
        requireRange(lower, upper);

        if (command.features() != null) {
            group.setFeatures(requireText(command.features(), MAX_FEATURES, "识别特征不能为空"));
        }
        if (command.sizeBasis() != null) {
            group.setSizeBasis(requireText(command.sizeBasis(), MAX_BASIS, "计数依据不能为空"));
        }
        if (command.pointIds() != null) {
            group.setPointIds(requirePointIds(command.pointIds()));
        }
        group.setMinSize(lower);
        group.setMaxSize(upper);
        group.setUpdatedAt(LocalDateTime.now(clock));
        groups.updateById(group);

        return toDetail(group, code.trim(), species.selectById(group.getSpeciesId()), loadPoints(group.getPointIds()));
    }

    @Transactional
    public CreatedSighting recordSighting(String groupCode, CreateSightingCommand command, Long operatorId) {
        AnimalGroup group = findByCode(groupCode);
        requireNotGone(group);
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.pointId() == null) {
            throw new BusinessException(30301, "目击点位必填");
        }
        BasePoint point = points.selectById(command.pointId());
        if (point == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        if (command.seenAt() == null) {
            throw new BusinessException(30301, "目击时间必填");
        }
        if (command.countEstimate() != null && command.countEstimate() <= 0) {
            throw new BusinessException(30301, "目击数量必须为正整数");
        }
        String behavior = command.behavior() == null ? null
                : requireText(command.behavior(), MAX_BEHAVIOR, "行为描述不能超过 " + MAX_BEHAVIOR + " 字");
        validateFiles(command.fileIds());

        LocalDateTime seenAt = command.seenAt().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now(clock);
        Sighting sighting = new Sighting();
        sighting.setGroupId(group.getId());
        sighting.setPointId(point.getId());
        sighting.setSeenAt(seenAt);
        sighting.setCountEstimate(command.countEstimate());
        sighting.setBehavior(behavior);
        sighting.setFileIds(command.fileIds() == null ? null : List.copyOf(command.fileIds()));
        sighting.setRecorderId(operatorId);
        sighting.setAssignStatus("ASSIGNED");
        sighting.setCreatedAt(now);
        sighting.setUpdatedAt(now);
        sighting.setCreatedBy(operatorId);
        sightings.insert(sighting);

        if (group.getLastSeenAt() == null || seenAt.isAfter(group.getLastSeenAt())) {
            group.setLastSeenAt(seenAt);
            group.setUpdatedAt(now);
            groups.updateById(group);
        }
        return new CreatedSighting(sighting.getId());
    }

    /**
     * 创建待归属目击：尚未确认属于哪个群体时先落库，后台再指派。
     */
    @Transactional
    public CreatedSighting recordUnassignedSighting(CreateSightingCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.pointId() == null) {
            throw new BusinessException(30301, "目击点位必填");
        }
        BasePoint point = points.selectById(command.pointId());
        if (point == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        if (command.seenAt() == null) {
            throw new BusinessException(30301, "目击时间必填");
        }
        if (command.countEstimate() != null && command.countEstimate() <= 0) {
            throw new BusinessException(30301, "目击数量必须为正整数");
        }
        String behavior = command.behavior() == null ? null
                : requireText(command.behavior(), MAX_BEHAVIOR, "行为描述不能超过 " + MAX_BEHAVIOR + " 字");
        validateFiles(command.fileIds());

        LocalDateTime seenAt = command.seenAt().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Sighting sighting = new Sighting();
        sighting.setGroupId(null);
        sighting.setPointId(point.getId());
        sighting.setSeenAt(seenAt);
        sighting.setCountEstimate(command.countEstimate());
        sighting.setBehavior(behavior);
        sighting.setFileIds(command.fileIds() == null ? null : List.copyOf(command.fileIds()));
        sighting.setRecorderId(operatorId);
        sighting.setAssignStatus("UNASSIGNED");
        sighting.setCreatedAt(now);
        sighting.setUpdatedAt(now);
        sighting.setCreatedBy(operatorId);
        sightings.insert(sighting);
        return new CreatedSighting(sighting.getId());
    }

    public PageResult<SightingView> searchSightings(SightingFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Sighting> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<Sighting> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            if (trimToNull(filter.groupCode()) != null) {
                AnimalGroup group = findByCode(filter.groupCode().trim());
                query.eq(Sighting::getGroupId, group.getId());
            }
            if (trimToNull(filter.assignStatus()) != null) {
                query.eq(Sighting::getAssignStatus, filter.assignStatus().trim());
            }
            if (filter.pointId() != null) {
                query.eq(Sighting::getPointId, filter.pointId());
            }
        }
        query.orderByDesc(Sighting::getSeenAt).orderByDesc(Sighting::getId);

        IPage<Sighting> result = sightings.selectPage(page, query);
        Map<Long, BasePoint> pointMap = loadPoints(result.getRecords().stream()
                .map(Sighting::getPointId).filter(Objects::nonNull).toList());
        Map<Long, AnimalGroup> groupMap = new HashMap<>();
        List<SightingView> rows = result.getRecords().stream()
                .map(row -> {
                    AnimalGroup group = row.getGroupId() == null ? null
                            : groupMap.computeIfAbsent(row.getGroupId(), groups::selectById);
                    return new SightingView(
                            row.getId(),
                            group == null ? null : group.getGroupCode(),
                            toPointRef(pointMap.get(row.getPointId())),
                            toOffset(row.getSeenAt()),
                            row.getCountEstimate(),
                            row.getBehavior(),
                            row.getFileIds(),
                            row.getRecorderId(),
                            row.getAssignStatus(),
                            toOffset(row.getCreatedAt()));
                })
                .toList();
        return PageResult.of(rows, safe.getPage(), safe.getSize(), result.getTotal());
    }

    private void requireRange(int lower, int upper) {
        if (lower < 1 || upper < lower) {
            throw new BusinessException(ErrorCode.SIZE_RANGE_INVALID);
        }
    }

    private BaseSpecies requireSpecies(Long speciesId) {
        if (speciesId == null) {
            throw new BusinessException(30301, "物种必填");
        }
        BaseSpecies row = species.selectById(speciesId);
        if (row == null) {
            throw new BusinessException(30410, "物种不存在");
        }
        return row;
    }

    private List<Long> requirePointIds(List<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            throw new BusinessException(30301, "至少选择一个活动点位");
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long pointId : pointIds) {
            if (pointId == null) {
                throw new BusinessException(30301, "点位 id 不能为空");
            }
            distinct.add(pointId);
        }
        List<Long> ids = List.copyOf(distinct);
        List<BasePoint> found = points.selectBatchIds(ids);
        if (found.size() != ids.size()) {
            throw new BusinessException(30410, "点位不存在");
        }
        return ids;
    }

    private void validateFiles(List<String> fileIds) {
        if (fileIds == null) {
            return;
        }
        fileIds.forEach(fileId -> {
            if (fileId == null || fileId.isBlank()) {
                throw new BusinessException(30301, "附件 fileId 不能为空");
            }
        });
    }

    private AnimalGroup findByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(30301, "群体编号不能为空");
        }
        AnimalGroup group = groups.selectByCode(code.trim());
        if (group == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return group;
    }

    private void requireNotGone(AnimalGroup group) {
        if (group.getStatus() == GroupStatus.GONE) {
            String merged = group.getMergedIntoCode();
            throw new BusinessException(30505, merged == null
                    ? "该群体已标记消失，不能继续登记或修改"
                    : "该群体已合并到 " + merged + "，请到主群体操作");
        }
    }

    private String requireText(String value, int maxLength, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(30301, message);
        }
        if (text.length() > maxLength) {
            throw new BusinessException(30301, message);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<Long, BasePoint> loadPoints(List<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>(pointIds);
        Map<Long, BasePoint> map = new HashMap<>();
        points.selectBatchIds(ids).forEach(point -> map.put(point.getId(), point));
        return map;
    }

    private GroupSummary toSummary(AnimalGroup group, BaseSpecies speciesRow, Map<Long, BasePoint> pointMap) {
        return new GroupSummary(group.getGroupCode(), toSpeciesRef(speciesRow), group.getFeatures(),
                group.getMinSize(), group.getMaxSize(), group.getSizeBasis(), group.getStatus(),
                group.getStatus().label(), pointRefs(group.getPointIds(), pointMap), toOffset(group.getLastSeenAt()));
    }

    private GroupDetail toDetail(AnimalGroup group, String queriedCode, BaseSpecies speciesRow,
                                 Map<Long, BasePoint> pointMap) {
        return new GroupDetail(group.getGroupCode(), queriedCode, toSpeciesRef(speciesRow), group.getFeatures(),
                group.getMinSize(), group.getMaxSize(), group.getSizeBasis(), group.getStatus(),
                group.getStatus().label(), pointRefs(group.getPointIds(), pointMap),
                toOffset(group.getLastSeenAt()),
                group.getMergedFrom() == null ? List.of() : List.copyOf(group.getMergedFrom()),
                group.getSplitFrom(), group.getMergedIntoCode(),
                toOffset(group.getCreatedAt()), toOffset(group.getUpdatedAt()));
    }

    private SpeciesRef toSpeciesRef(BaseSpecies row) {
        return row == null ? null : new SpeciesRef(row.getId(), row.getCommonName(), row.getCategory());
    }

    private List<PointRef> pointRefs(List<Long> pointIds, Map<Long, BasePoint> pointMap) {
        if (pointIds == null) {
            return List.of();
        }
        return pointIds.stream().map(pointMap::get).filter(Objects::nonNull)
                .map(this::toPointRef).toList();
    }

    private PointRef toPointRef(BasePoint point) {
        return point == null ? null : new PointRef(point.getId(), point.getPointName());
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }
}
