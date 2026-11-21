package com.just.critter.service.rescue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.rescue.ReportCodeGenerator;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生上报：五类必填校验、RP 当日发号、24 小时时限、同点位未关闭去重提示。
 * 重复时仍保留新单（每条上报都要留痕），只把原单号回给前端决定是否补充。
 */
@Service
public class ReportService {

    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_CONTACT_LENGTH = 64;
    private static final int DEDUP_HOURS = 24;

    private final ReportMapper reports;
    private final BasePointMapper points;
    private final AnimalMapper animals;
    private final ReportCodeGenerator codeGenerator;
    private final OrderMapper orders;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ReportService(ReportMapper reports,
                         BasePointMapper points,
                         AnimalMapper animals,
                         ReportCodeGenerator codeGenerator,
                         OrderMapper orders,
                         AuditLogService auditLog,
                         Clock clock) {
        this.reports = reports;
        this.points = points;
        this.animals = animals;
        this.codeGenerator = codeGenerator;
        this.orders = orders;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public CreatedReport create(CreateReportCommand command, Long reporterId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, java.util.Map.of("field", "body"));
        }
        ReportType type = parseType(command.type());
        if (command.pointId() == null) {
            throw new BusinessException(30301, "点位必填");
        }
        List<String> photos = requirePhotos(command.photos());
        String description = requireDescription(command.description());
        String contact = requireContact(command.contact());

        BasePoint point = points.selectById(command.pointId());
        if (point == null) {
            throw new BusinessException(30410, "点位不存在");
        }
        Long animalId = resolveAnimalId(command.animalCode());

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Report duplicate = findDuplicate(point.getId(), now);

        Report report = new Report();
        report.setReportNo(codeGenerator.next());
        report.setType(type);
        report.setPointId(point.getId());
        report.setDescription(description);
        report.setFileIds(List.copyOf(photos));
        report.setReporterId(reporterId);
        report.setAnimalId(animalId);
        report.setStatus(ReportStatus.PENDING);
        report.setContact(contact);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        report.setCreatedBy(reporterId);
        reports.insert(report);

        return new CreatedReport(
                report.getReportNo(),
                report.getStatus(),
                toOffset(now.plusHours(DEDUP_HOURS)),
                duplicate != null,
                duplicate == null ? null : duplicate.getReportNo());
    }

    public PageResult<ReportSummary> search(ReportFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Report> page = new Page<>(safe.getPage(), safe.getSize());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        LambdaQueryWrapper<Report> query = new LambdaQueryWrapper<>();
        if (filter != null) {
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                query.in(Report::getStatus, filter.statuses());
            }
            if (filter.type() != null) {
                query.eq(Report::getType, filter.type());
            }
            if (filter.pointId() != null) {
                query.eq(Report::getPointId, filter.pointId());
            }
            if (filter.overdue() != null) {
                query.ne(Report::getStatus, ReportStatus.CLOSED);
                if (filter.overdue()) {
                    query.lt(Report::getCreatedAt, now.minusHours(DEDUP_HOURS));
                } else {
                    query.ge(Report::getCreatedAt, now.minusHours(DEDUP_HOURS));
                }
            }
        }
        query.orderByDesc(Report::getCreatedAt);

        IPage<Report> result = reports.selectPage(page, query);
        Set<Long> pointIds = result.getRecords().stream()
                .map(Report::getPointId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        List<BasePoint> pointList = pointIds.isEmpty() ? List.of() : points.selectBatchIds(pointIds);
        Map<Long, BasePoint> pointMap = pointList.stream()
                .collect(Collectors.toMap(BasePoint::getId, point -> point, (a, b) -> a));
        List<ReportSummary> list = result.getRecords().stream()
                .map(report -> toSummary(report, pointMap.get(report.getPointId()), now))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    public ReportDetail detail(Long id, CurrentUser viewer) {
        Report report = requireReport(id);
        BasePoint point = points.selectById(report.getPointId());
        Animal animal = report.getAnimalId() == null ? null : animals.selectById(report.getAnimalId());
        Order order = orders.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getReportId, report.getId())
                .orderByDesc(Order::getCreatedAt)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        boolean contactVisible = viewer != null
                && (viewer.isAdmin()
                || (report.getReporterId() != null && viewer.id() == report.getReporterId()));
        return new ReportDetail(
                report.getId(),
                report.getReportNo(),
                report.getType(),
                report.getPointId(),
                point == null ? null : point.getPointName(),
                report.getDescription(),
                report.getFileIds() == null ? List.of() : List.copyOf(report.getFileIds()),
                report.getStatus(),
                overdue(report, now),
                report.getCreatedAt(),
                deadline(report.getCreatedAt()),
                report.getReporterId(),
                report.getAnimalId(),
                animal == null ? null : animal.getCode(),
                contactVisible ? report.getContact() : null,
                order == null ? null : order.getOrderNo());
    }

    @Transactional
    public ClosedReport close(Long id, String reason) {
        Report report = requireReport(id);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID, "仅待受理上报可关闭");
        }
        String closeReason = trimToNull(reason);
        if (closeReason == null) {
            throw new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED, "请填写关闭原因");
        }
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        report.setStatus(ReportStatus.CLOSED);
        report.setCloseReason(closeReason);
        report.setUpdatedAt(now);
        reports.updateById(report);
        auditLog.success("REPORT_CLOSE", "REPORT", report.getReportNo(),
                Map.of("status", ReportStatus.PENDING.name()),
                Map.of("status", ReportStatus.CLOSED.name(), "closeReason", closeReason));
        return new ClosedReport(report.getReportNo(), report.getStatus(), closeReason);
    }

    private Report requireReport(Long id) {
        Report report = id == null ? null : reports.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "上报不存在");
        }
        return report;
    }

    private ReportSummary toSummary(Report report, BasePoint point, LocalDateTime now) {
        return new ReportSummary(
                report.getId(),
                report.getReportNo(),
                report.getType(),
                report.getPointId(),
                point == null ? null : point.getPointName(),
                report.getDescription(),
                report.getFileIds() == null ? List.of() : List.copyOf(report.getFileIds()),
                report.getStatus(),
                overdue(report, now),
                report.getCreatedAt(),
                deadline(report.getCreatedAt()),
                report.getReporterId());
    }

    private boolean overdue(Report report, LocalDateTime now) {
        return report.getStatus() != ReportStatus.CLOSED
                && report.getCreatedAt() != null
                && report.getCreatedAt().plusHours(DEDUP_HOURS).isBefore(now);
    }

    private OffsetDateTime deadline(LocalDateTime createdAt) {
        return createdAt == null ? null : toOffset(createdAt.plusHours(DEDUP_HOURS));
    }

    private ReportType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(30301, "上报类型必填");
        }
        try {
            return ReportType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(30303, "上报类型非法：" + raw);
        }
    }

    private List<String> requirePhotos(List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            throw new BusinessException(30301, "请至少上传 1 张照片");
        }
        for (String photo : photos) {
            if (photo == null || photo.isBlank()) {
                throw new BusinessException(30301, "照片 fileId 不能为空");
            }
        }
        return photos;
    }

    private String requireDescription(String raw) {
        String description = trimToNull(raw);
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new BusinessException(30301, "描述至少 10 个字");
        }
        return description;
    }

    private String requireContact(String raw) {
        String contact = trimToNull(raw);
        if (contact != null && contact.length() > MAX_CONTACT_LENGTH) {
            throw new BusinessException(30301, "联系方式最多 64 个字符");
        }
        return contact;
    }

    private Long resolveAnimalId(String rawCode) {
        String code = trimToNull(rawCode);
        if (code == null) {
            return null;
        }
        Animal animal = animals.selectByCode(code);
        if (animal == null) {
            throw new BusinessException(30410, "档案不存在：" + code);
        }
        return animal.getId();
    }

    private Report findDuplicate(Long pointId, LocalDateTime now) {
        List<Report> matched = reports.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getPointId, pointId)
                .ne(Report::getStatus, ReportStatus.CLOSED)
                .ge(Report::getCreatedAt, now.minusHours(DEDUP_HOURS))
                .orderByAsc(Report::getCreatedAt)
                .last("LIMIT 1"));
        return matched.isEmpty() ? null : matched.getFirst();
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
}
