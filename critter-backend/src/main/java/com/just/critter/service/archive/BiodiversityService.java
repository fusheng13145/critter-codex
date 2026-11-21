package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.Season;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.domain.model.Occurrence;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.OccurrenceMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物种名录、点位字典与出现记录。点位与物种一期只读；出现记录数量只收枚举，
 * 季节筛选把枚举换算成月份条件，不新增数据库字段。
 */
@Service
public class BiodiversityService {

    private static final int MAX_HABITAT = 32;

    private final BaseSpeciesMapper species;
    private final BasePointMapper points;
    private final OccurrenceMapper occurrences;
    private final Clock clock;

    public BiodiversityService(BaseSpeciesMapper species,
                               BasePointMapper points,
                               OccurrenceMapper occurrences,
                               Clock clock) {
        this.species = species;
        this.points = points;
        this.occurrences = occurrences;
        this.clock = clock;
    }

    public List<SpeciesView> listSpecies(SpeciesFilter filter) {
        LambdaQueryWrapper<BaseSpecies> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            String keyword = trimToNull(filter.keyword());
            if (keyword != null) {
                query.and(w -> w.like(BaseSpecies::getCommonName, keyword)
                        .or().like(BaseSpecies::getScientificName, keyword));
            }
            String category = trimToNull(filter.category());
            if (category != null) {
                query.eq(BaseSpecies::getCategory, category);
            }
        }
        query.orderByAsc(BaseSpecies::getCommonName);
        return species.selectList(query).stream()
                .map(row -> new SpeciesView(row.getId(), row.getCommonName(), row.getScientificName(),
                        row.getCategory(), row.getProtectionLevel(), row.getNote()))
                .toList();
    }

    public List<PointView> listPoints() {
        LambdaQueryWrapper<BasePoint> query = new LambdaQueryWrapper<BasePoint>()
                .eq(BasePoint::getEnabled, true)
                .orderByAsc(BasePoint::getArea)
                .orderByAsc(BasePoint::getPointName);
        return points.selectList(query).stream()
                .map(row -> new PointView(row.getId(), row.getPointName(), row.getArea(),
                        row.getX(), row.getY(), Boolean.TRUE.equals(row.getEnabled()), row.getNote()))
                .toList();
    }

    @Transactional
    public CreatedOccurrence createOccurrence(CreateOccurrenceCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        if (command.speciesId() == null) {
            throw new BusinessException(30301, "物种必填");
        }
        if (species.selectById(command.speciesId()) == null) {
            throw new BusinessException(30410, "物种不存在");
        }
        if (command.pointId() == null) {
            throw new BusinessException(30301, "点位必填");
        }
        if (points.selectById(command.pointId()) == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        if (command.occurredAt() == null) {
            throw new BusinessException(30301, "出现时间必填");
        }
        if (command.fileIds() == null || command.fileIds().isEmpty()) {
            throw new BusinessException(30301, "至少上传一张照片");
        }
        command.fileIds().forEach(fileId -> {
            if (fileId == null || fileId.isBlank()) {
                throw new BusinessException(30301, "照片 fileId 不能为空");
            }
        });
        String habitat = command.habitat() == null ? null
                : requireText(command.habitat(), MAX_HABITAT, "生境描述不能超过 " + MAX_HABITAT + " 字");

        LocalDateTime occurredAt = command.occurredAt().atZoneSameInstant(AppConfig.APP_ZONE).toLocalDateTime();
        LocalDateTime now = LocalDateTime.now(clock);
        Occurrence row = new Occurrence();
        row.setSpeciesId(command.speciesId());
        row.setPointId(command.pointId());
        row.setOccurredAt(occurredAt);
        row.setScale(command.scale());
        row.setHabitat(habitat);
        row.setFileIds(List.copyOf(command.fileIds()));
        row.setRecorderId(operatorId);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(operatorId);
        occurrences.insert(row);

        return new CreatedOccurrence(row.getId());
    }

    public PageResult<OccurrenceView> searchOccurrences(OccurrenceFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Occurrence> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<Occurrence> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            if (filter.speciesId() != null) {
                query.eq(Occurrence::getSpeciesId, filter.speciesId());
            }
            if (filter.pointId() != null) {
                query.eq(Occurrence::getPointId, filter.pointId());
            }
            if (filter.season() != null) {
                query.apply("MONTH(occurred_at) IN (" + seasonMonths(filter.season()) + ")");
            }
        }
        query.orderByDesc(Occurrence::getOccurredAt).orderByDesc(Occurrence::getId);

        IPage<Occurrence> result = occurrences.selectPage(page, query);
        Set<Long> pointIds = result.getRecords().stream()
                .map(Occurrence::getPointId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BasePoint> pointMap = loadPoints(pointIds);
        Map<Long, BaseSpecies> speciesMap = new HashMap<>();
        List<OccurrenceView> rows = result.getRecords().stream()
                .map(row -> {
                    BaseSpecies speciesRow = speciesMap.computeIfAbsent(row.getSpeciesId(), species::selectById);
                    BasePoint point = pointMap.get(row.getPointId());
                    return new OccurrenceView(
                            row.getId(),
                            row.getSpeciesId(),
                            speciesRow == null ? null : speciesRow.getCommonName(),
                            point == null ? null : new PointRef(point.getId(), point.getPointName()),
                            toOffset(row.getOccurredAt()),
                            row.getScale(),
                            row.getScale() == null ? null : row.getScale().label(),
                            row.getHabitat(),
                            row.getFileIds(),
                            row.getRecorderId(),
                            toOffset(row.getCreatedAt()));
                })
                .toList();
        return PageResult.of(rows, safe.getPage(), safe.getSize(), result.getTotal());
    }

    private String seasonMonths(Season season) {
        return season.months().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Map<Long, BasePoint> loadPoints(Set<Long> pointIds) {
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BasePoint> map = new HashMap<>();
        points.selectBatchIds(new LinkedHashSet<>(pointIds)).forEach(point -> map.put(point.getId(), point));
        return map;
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

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }
}
