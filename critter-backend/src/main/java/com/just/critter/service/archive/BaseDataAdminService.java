package com.just.critter.service.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.BaseSpecies;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.PointReferenceMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基础数据维护：点位与物种的新增、修改、停用。
 * 点位默认不物理删除，enabled=false 停用后仍保留历史引用；仅未被引用点位可物理删除。
 * 物种同样只改不删。
 */
@Service
public class BaseDataAdminService {

    private final BasePointMapper points;
    private final BaseSpeciesMapper species;
    private final PointReferenceMapper pointRefs;
    private final AuditLogService auditLog;
    private final Clock clock;

    public BaseDataAdminService(BasePointMapper points, BaseSpeciesMapper species,
                                PointReferenceMapper pointRefs,
                                AuditLogService auditLog, Clock clock) {
        this.points = points;
        this.species = species;
        this.pointRefs = pointRefs;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * 点位维护列表：含停用点位与引用量，供管理端判断能否删除。
     */
    public List<PointAdminView> listPoints() {
        return points.selectList(new LambdaQueryWrapper<BasePoint>()
                        .orderByAsc(BasePoint::getArea)
                        .orderByAsc(BasePoint::getPointName))
                .stream()
                .map(this::toAdminView)
                .toList();
    }

    @Transactional
    public PointView createPoint(SavePointCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String name = requireText(command.pointName(), "点位名称", 64, true);
        requireDuplicatePointName(name, null);
        validateCoordinate(command.x(), command.y());
        String area = optionalText(command.area(), "所属区域", 32);
        String note = optionalText(command.note(), "备注", 255);

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        BasePoint point = new BasePoint();
        point.setPointName(name);
        point.setArea(area);
        point.setX(command.x());
        point.setY(command.y());
        point.setEnabled(command.enabled() == null || command.enabled());
        point.setNote(note);
        point.setCreatedAt(now);
        point.setUpdatedAt(now);
        point.setCreatedBy(operatorId);
        points.insert(point);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "POINT_CREATE", "POINT",
                String.valueOf(point.getId()), null, toView(point));
        return toView(point);
    }

    @Transactional
    public PointView updatePoint(Long id, SavePointCommand command, Long operatorId) {
        BasePoint point = requirePoint(id);
        boolean changed = false;
        if (command.pointName() != null) {
            String name = requireText(command.pointName(), "点位名称", 64, true);
            requireDuplicatePointName(name, id);
            point.setPointName(name);
            changed = true;
        }
        if (command.area() != null) {
            point.setArea(optionalText(command.area(), "所属区域", 32));
            changed = true;
        }
        if (command.x() != null) {
            validateCoordinate(command.x(), command.y());
            point.setX(command.x());
            changed = true;
        }
        if (command.y() != null) {
            validateCoordinate(point.getX(), command.y());
            point.setY(command.y());
            changed = true;
        }
        if (command.enabled() != null) {
            point.setEnabled(command.enabled());
            changed = true;
        }
        if (command.note() != null) {
            point.setNote(optionalText(command.note(), "备注", 255));
            changed = true;
        }
        if (!changed) {
            throw new BusinessException(30301, "至少修改一个点位字段");
        }
        point.setUpdatedAt(LocalDateTime.now(clock).withNano(0));
        points.updateById(point);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "POINT_UPDATE", "POINT",
                String.valueOf(id), null, toView(point));
        return toView(point);
    }

    @Transactional
    public SpeciesView createSpecies(SaveSpeciesCommand command, Long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String commonName = requireText(command.commonName(), "物种俗名", 64, true);
        requireDuplicateSpeciesName(commonName, null);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        BaseSpecies row = new BaseSpecies();
        row.setCommonName(commonName);
        row.setScientificName(optionalText(command.scientificName(), "学名", 128));
        row.setCategory(optionalText(command.category(), "类别", 16));
        row.setProtectionLevel(optionalText(command.protectionLevel(), "保护级别", 16));
        row.setNote(optionalText(command.note(), "备注", 255));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(operatorId);
        species.insert(row);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "SPECIES_CREATE", "SPECIES",
                String.valueOf(row.getId()), null, toView(row));
        return toView(row);
    }

    @Transactional
    public SpeciesView updateSpecies(Long id, SaveSpeciesCommand command, Long operatorId) {
        BaseSpecies row = requireSpecies(id);
        boolean changed = false;
        if (command.commonName() != null) {
            String name = requireText(command.commonName(), "物种俗名", 64, true);
            requireDuplicateSpeciesName(name, id);
            row.setCommonName(name);
            changed = true;
        }
        if (command.scientificName() != null) {
            row.setScientificName(optionalText(command.scientificName(), "学名", 128));
            changed = true;
        }
        if (command.category() != null) {
            row.setCategory(optionalText(command.category(), "类别", 16));
            changed = true;
        }
        if (command.protectionLevel() != null) {
            row.setProtectionLevel(optionalText(command.protectionLevel(), "保护级别", 16));
            changed = true;
        }
        if (command.note() != null) {
            row.setNote(optionalText(command.note(), "备注", 255));
            changed = true;
        }
        if (!changed) {
            throw new BusinessException(30301, "至少修改一个物种字段");
        }
        row.setUpdatedAt(LocalDateTime.now(clock).withNano(0));
        species.updateById(row);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "SPECIES_UPDATE", "SPECIES",
                String.valueOf(id), null, toView(row));
        return toView(row);
    }

    /**
     * 删除点位：未被任何业务表引用时物理删除；有引用时拒绝并提示改用停用，
     * 避免历史目击、上报、巡查记录失去落点。
     */
    @Transactional
    public void deletePoint(Long id, Long operatorId) {
        BasePoint point = requirePoint(id);
        PointAdminView view = toAdminView(point);
        if (view.referenceCount() > 0) {
            throw new BusinessException(ErrorCode.POINT_REFERENCED,
                    Map.of("pointId", id, "referenceCount", view.referenceCount(),
                            "animalCount", view.animalCount(), "groupCount", view.groupCount(),
                            "sightingCount", view.sightingCount(), "reportCount", view.reportCount(),
                            "patrolCount", view.patrolCount()));
        }
        points.deleteById(id);
        auditLog.successAs(operatorId, "ANIMAL_ADMIN", "POINT_DELETE", "POINT",
                String.valueOf(id), toView(point), null);
    }

    private void requireDuplicatePointName(String name, Long excludeId) {
        LambdaQueryWrapper<BasePoint> query = new LambdaQueryWrapper<BasePoint>()
                .eq(BasePoint::getPointName, name);
        if (excludeId != null) {
            query.ne(BasePoint::getId, excludeId);
        }
        if (points.selectCount(query) > 0) {
            throw new BusinessException(30411, "点位名称已存在");
        }
    }

    private void requireDuplicateSpeciesName(String name, Long excludeId) {
        LambdaQueryWrapper<BaseSpecies> query = new LambdaQueryWrapper<BaseSpecies>()
                .eq(BaseSpecies::getCommonName, name);
        if (excludeId != null) {
            query.ne(BaseSpecies::getId, excludeId);
        }
        if (species.selectCount(query) > 0) {
            throw new BusinessException(30411, "物种俗名已存在");
        }
    }

    private BasePoint requirePoint(Long id) {
        BasePoint point = id == null ? null : points.selectById(id);
        if (point == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "点位不存在");
        }
        return point;
    }

    private BaseSpecies requireSpecies(Long id) {
        BaseSpecies row = id == null ? null : species.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "物种不存在");
        }
        return row;
    }

    private void validateCoordinate(BigDecimal x, BigDecimal y) {
        if (x != null && (x.compareTo(BigDecimal.ZERO) < 0 || x.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException(30301, "x 坐标必须在 0–100 之间");
        }
        if (y != null && (y.compareTo(BigDecimal.ZERO) < 0 || y.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException(30301, "y 坐标必须在 0–100 之间");
        }
    }

    private String requireText(String value, String label, int max, boolean required) {
        String text = optionalText(value, label, max);
        if (required && text == null) {
            throw new BusinessException(30301, label + "必填");
        }
        return text;
    }

    private String optionalText(String value, String label, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new BusinessException(30301, label + "不能超过 " + max + " 个字");
        }
        return text;
    }

    private PointView toView(BasePoint point) {
        return new PointView(point.getId(), point.getPointName(), point.getArea(), point.getX(), point.getY(),
                Boolean.TRUE.equals(point.getEnabled()), point.getNote());
    }

    private PointAdminView toAdminView(BasePoint point) {
        long animals = pointRefs.countAnimals(point.getId());
        long groups = pointRefs.countGroups(point.getId());
        long sightings = pointRefs.countSightings(point.getId());
        long occurrences = pointRefs.countOccurrences(point.getId());
        long reports = pointRefs.countReports(point.getId());
        long patrols = pointRefs.countPatrols(point.getId());
        long total = animals + groups + sightings + occurrences + reports + patrols;
        return new PointAdminView(point.getId(), point.getPointName(), point.getArea(), point.getX(), point.getY(),
                Boolean.TRUE.equals(point.getEnabled()), point.getNote(),
                animals, groups, sightings, occurrences, reports, patrols, total, total == 0);
    }

    private SpeciesView toView(BaseSpecies row) {
        return new SpeciesView(row.getId(), row.getCommonName(), row.getScientificName(),
                row.getCategory(), row.getProtectionLevel(), row.getNote());
    }
}
