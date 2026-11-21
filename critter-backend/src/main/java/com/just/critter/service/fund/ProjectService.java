package com.just.critter.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.config.AppConfig;
import com.just.critter.domain.enums.ProjectEvent;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.fund.ProjectCodeGenerator;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.ProjectBudget;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.ProjectBudgetMapper;
import com.just.critter.mapper.ProjectDonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 募捐项目应用服务：发起（草稿/直接提交）、预算校验、审核上架、公开进度。
 *
 * <p>金额单位全部为分；预算合计不得超过目标金额（40101）。项目状态只走
 * {@link StateMachines#project()}，不在业务代码里直接改状态。</p>
 */
@Service
public class ProjectService {

    private static final int MAX_NAME = 64;
    private static final int MAX_PURPOSE = 2000;
    private static final int MAX_SUBJECT = 32;
    private static final int MAX_NOTE = 255;
    private static final int MAX_BUDGET_ITEMS = 50;
    private static final int MAX_REASON = 255;
    private static final int RECENT_DONATIONS = 10;

    /** 公开页可见状态；DRAFT/PENDING_AUDIT/REJECTED 仅在管理端可见。 */
    private static final Set<ProjectStatus> PUBLIC_STATUSES = EnumSet.of(
            ProjectStatus.FUNDING, ProjectStatus.PENDING_SETTLE,
            ProjectStatus.SETTLED, ProjectStatus.ENDED);

    private final ProjectMapper projects;
    private final ProjectBudgetMapper budgets;
    private final ProjectDonationMapper donations;
    private final ProjectCodeGenerator codeGenerator;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ProjectService(ProjectMapper projects,
                          ProjectBudgetMapper budgets,
                          ProjectDonationMapper donations,
                          ProjectCodeGenerator codeGenerator,
                          AuditLogService auditLog,
                          Clock clock) {
        this.projects = projects;
        this.budgets = budgets;
        this.donations = donations;
        this.codeGenerator = codeGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public CreatedProject create(ProjectDraftCommand command, CurrentUser actor) {
        ValidatedProject validated = validate(command);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Project project = new Project();
        project.setProjectNo(codeGenerator.next());
        applyDraft(project, validated);
        project.setRaisedAmount(0);
        project.setSpentAmount(0);
        project.setDeleted(0);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setCreatedBy(actor == null ? null : actor.id());
        project.setStatus(command.submit()
                ? fire(ProjectStatus.DRAFT, ProjectEvent.SUBMIT, actor)
                : ProjectStatus.DRAFT);
        projects.insert(project);
        insertBudgets(project.getId(), validated.budget(), now, actor);

        auditLog.success("PROJECT_CREATE", "PROJECT", project.getProjectNo(),
                null, Map.of("status", project.getStatus().name(),
                        "targetAmount", project.getTargetAmount(),
                        "budgetTotal", validated.budgetTotal()));
        return new CreatedProject(project.getProjectNo(), project.getStatus(), toOffset(now));
    }

    @Transactional
    public ProjectDetail update(String projectNo, ProjectDraftCommand command, CurrentUser actor) {
        Project project = requireProject(projectNo);
        if (project.getStatus() != ProjectStatus.DRAFT && project.getStatus() != ProjectStatus.REJECTED) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID,
                    "仅草稿或被驳回项目可修改");
        }
        ValidatedProject validated = validate(command);
        ProjectStatus beforeStatus = project.getStatus();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        applyDraft(project, validated);
        project.setUpdatedAt(now);

        if (command.submit()) {
            ProjectEvent event = project.getStatus() == ProjectStatus.DRAFT
                    ? ProjectEvent.SUBMIT : ProjectEvent.RESUBMIT;
            project.setStatus(fire(project.getStatus(), event, actor));
            project.setAuditorId(null);
            project.setAuditReason(null);
        }

        budgets.delete(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getProjectId, project.getId()));
        insertBudgets(project.getId(), validated.budget(), now, actor);
        projects.updateById(project);

        auditLog.success("PROJECT_UPDATE", "PROJECT", project.getProjectNo(),
                Map.of("previousStatus", beforeStatus.name()),
                Map.of("status", project.getStatus().name(),
                        "budgetTotal", validated.budgetTotal()));
        return assembleDetail(project, validated.budget().stream()
                .map(item -> new ProjectBudgetItem(item.subject(), item.amount(), item.note()))
                .toList(), List.of(), 0, true);
    }

    @Transactional
    public ProjectAuditResult audit(String projectNo, ProjectAuditCommand command, CurrentUser actor) {
        Project project = requireProject(projectNo);
        if (project.getStatus() != ProjectStatus.PENDING_AUDIT) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID, "仅待审核项目可审核");
        }
        ProjectEvent event = parseDecision(command == null ? null : command.decision());
        String reason = trimToNull(command == null ? null : command.reason());
        if (event == ProjectEvent.REJECT && reason == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写驳回理由");
        }
        if (reason != null && reason.length() > MAX_REASON) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "驳回理由最多 255 个字符");
        }

        ProjectStatus before = project.getStatus();
        ProjectStatus next = fire(before, event, actor);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        project.setStatus(next);
        project.setAuditorId(actor == null ? null : actor.id());
        project.setAuditReason(event == ProjectEvent.REJECT ? reason : null);
        project.setUpdatedAt(now);
        projects.updateById(project);

        auditLog.successAs(actor == null ? 0L : actor.id(),
                actor == null || actor.primaryRole() == null ? "ANIMAL_ADMIN" : actor.primaryRole(),
                event == ProjectEvent.APPROVE ? "PROJECT_APPROVE" : "PROJECT_REJECT",
                "PROJECT", project.getProjectNo(),
                Map.of("status", before.name()),
                Map.of("status", next.name(), "auditReason", reason == null ? "" : reason));
        return new ProjectAuditResult(project.getProjectNo(), next, project.getAuditReason(),
                project.getAuditorId(), toOffset(now), allowedActions(next, true));
    }

    public PageResult<ProjectSummary> search(ProjectFilter filter, PageQuery pageQuery, boolean admin) {
        PageQuery safe = pageQuery.normalized();
        Page<Project> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<Project> query = new LambdaQueryWrapper<>();

        List<ProjectStatus> statuses = filter == null ? null : filter.statuses();
        if (!admin) {
            if (statuses == null || statuses.isEmpty()) {
                query.in(Project::getStatus, PUBLIC_STATUSES);
            } else {
                List<ProjectStatus> visible = statuses.stream()
                        .filter(PUBLIC_STATUSES::contains)
                        .toList();
                if (visible.isEmpty()) {
                    return PageResult.of(List.of(), safe.getPage(), safe.getSize(), 0);
                }
                query.in(Project::getStatus, visible);
            }
        } else if (statuses != null && !statuses.isEmpty()) {
            query.in(Project::getStatus, statuses);
        }
        if (filter != null && filter.targetType() != null) {
            query.eq(Project::getTargetType, filter.targetType());
        }
        String keyword = filter == null ? null : trimToNull(filter.keyword());
        if (keyword != null) {
            query.and(w -> w.like(Project::getName, keyword)
                    .or().like(Project::getPurpose, keyword));
        }
        query.orderByDesc(Project::getCreatedAt).orderByDesc(Project::getId);

        IPage<Project> result = projects.selectPage(page, query);
        Map<Long, Long> counts = donationCounts(result.getRecords());
        LocalDate today = LocalDate.now(clock);
        List<ProjectSummary> list = result.getRecords().stream()
                .map(project -> new ProjectSummary(
                        project.getProjectNo(),
                        project.getName(),
                        project.getTargetType(),
                        intValue(project.getTargetAmount()),
                        intValue(project.getRaisedAmount()),
                        intValue(project.getSpentAmount()),
                        progressPercent(project),
                        project.getStartDate(),
                        project.getEndDate(),
                        daysRemaining(project, today),
                        project.getStatus(),
                        counts.getOrDefault(project.getId(), 0L),
                        project.getCoverFileId(),
                        toOffset(project.getCreatedAt())))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    public ProjectDetail detail(String projectNo, boolean admin) {
        Project project = requireProject(projectNo);
        if (!admin && !PUBLIC_STATUSES.contains(project.getStatus())) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        List<ProjectBudget> rows = budgets.selectList(new LambdaQueryWrapper<ProjectBudget>()
                .eq(ProjectBudget::getProjectId, project.getId())
                .orderByAsc(ProjectBudget::getId));
        List<ProjectBudgetItem> budget = rows == null ? List.of() : rows.stream()
                .map(row -> new ProjectBudgetItem(row.getSubject(), intValue(row.getAmount()),
                        row.getNote()))
                .toList();
        long count = donationCount(project.getId());
        List<ProjectDonation> recent = recentDonations(project.getId());
        return assembleDetail(project, budget, recent, count, admin);
    }

    public List<String> allowedActions(ProjectStatus status, boolean admin) {
        if (!admin || status == null) {
            return List.of();
        }
        return switch (status) {
            case DRAFT -> List.of("SUBMIT");
            case PENDING_AUDIT -> List.of("APPROVE", "REJECT");
            case REJECTED -> List.of("RESUBMIT");
            case PENDING_SETTLE -> List.of("SETTLE");
            default -> List.of();
        };
    }

    private ProjectDetail assembleDetail(Project project, List<ProjectBudgetItem> budget,
                                        List<ProjectDonation> recent, long donationCount,
                                        boolean admin) {
        int budgetTotal = budget.stream().mapToInt(ProjectBudgetItem::amount).sum();
        LocalDate today = LocalDate.now(clock);
        return new ProjectDetail(
                project.getProjectNo(),
                project.getName(),
                project.getPurpose(),
                project.getTargetType(),
                intValue(project.getTargetAmount()),
                intValue(project.getRaisedAmount()),
                intValue(project.getSpentAmount()),
                progressPercent(project),
                project.getStartDate(),
                project.getEndDate(),
                daysRemaining(project, today),
                project.getStatus(),
                project.getCoverFileId(),
                project.getAuditReason(),
                project.getAuditorId(),
                donationCount,
                budgetTotal,
                budget,
                recent,
                toOffset(project.getCreatedAt()),
                toOffset(project.getUpdatedAt()),
                allowedActions(project.getStatus(), admin));
    }

    private ValidatedProject validate(ProjectDraftCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        String name = requireText(command.name(), "项目名称", MAX_NAME);
        String purpose = requireText(command.purpose(), "用途说明", MAX_PURPOSE);
        ProjectTargetType targetType = parseTargetType(command.targetType());
        int targetAmount = requirePositive(command.targetAmount(), "目标金额");
        if (command.startDate() == null || command.endDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "项目起止日期必填");
        }
        if (command.endDate().isBefore(command.startDate())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "项目截止日期不能早于开始日期");
        }
        if (command.coverFileId() != null && command.coverFileId().trim().length() > 24) {
            throw new BusinessException(ErrorCode.FORMAT_INVALID, "封面文件编号不能超过 24 个字符");
        }
        if (command.budget() == null || command.budget().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "预算明细至少一项");
        }
        if (command.budget().size() > MAX_BUDGET_ITEMS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "预算明细最多 50 项");
        }

        List<BudgetItem> items = new ArrayList<>();
        long total = 0;
        for (ProjectDraftCommand.BudgetItem raw : command.budget()) {
            if (raw == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "预算明细不能为空");
            }
            String subject = requireText(raw.subject(), "预算科目", MAX_SUBJECT);
            int amount = requirePositive(raw.amount(), "预算金额");
            String note = trimToNull(raw.note());
            if (note != null && note.length() > MAX_NOTE) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "预算备注最多 255 个字符");
            }
            total += amount;
            items.add(new BudgetItem(subject, amount, note));
        }
        if (total > targetAmount) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("targetAmount", targetAmount);
            data.put("budgetTotal", (int) total);
            data.put("excessAmount", (int) (total - targetAmount));
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDS_TARGET, data);
        }
        return new ValidatedProject(name, purpose, targetType, targetAmount,
                command.startDate(), command.endDate(), List.copyOf(items),
                trimToNull(command.coverFileId()), (int) total);
    }

    private void applyDraft(Project project, ValidatedProject validated) {
        project.setName(validated.name());
        project.setPurpose(validated.purpose());
        project.setTargetType(validated.targetType());
        project.setTargetAmount(validated.targetAmount());
        project.setStartDate(validated.startDate());
        project.setEndDate(validated.endDate());
        project.setCoverFileId(validated.coverFileId());
    }

    private void insertBudgets(Long projectId, List<BudgetItem> items, LocalDateTime now,
                               CurrentUser actor) {
        for (BudgetItem item : items) {
            ProjectBudget budget = new ProjectBudget();
            budget.setProjectId(projectId);
            budget.setSubject(item.subject());
            budget.setAmount(item.amount());
            budget.setNote(item.note());
            budget.setCreatedAt(now);
            budget.setUpdatedAt(now);
            budget.setCreatedBy(actor == null ? null : actor.id());
            budgets.insert(budget);
        }
    }

    private Project requireProject(String projectNo) {
        String no = trimToNull(projectNo);
        if (no == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "项目编号必填");
        }
        Project project = projects.selectByProjectNo(no);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + no);
        }
        return project;
    }

    private ProjectStatus fire(ProjectStatus from, ProjectEvent event, CurrentUser actor) {
        return StateMachines.project().fire(from, event,
                TransitionContext.of(roleOf(actor)));
    }

    private ProjectEvent parseDecision(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "审核决定必填");
        }
        try {
            ProjectEvent event = ProjectEvent.valueOf(value.toUpperCase(Locale.ROOT));
            if (event == ProjectEvent.APPROVE || event == ProjectEvent.REJECT) {
                return event;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to enum error
        }
        throw new BusinessException(ErrorCode.ENUM_INVALID, "审核决定非法：" + raw);
    }

    private ProjectTargetType parseTargetType(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "关联对象类型必填");
        }
        try {
            return ProjectTargetType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, "关联对象类型非法：" + raw);
        }
    }

    private int requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + "必须大于 0");
        }
        return value;
    }

    private String requireText(String value, String field, int max) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + "必填");
        }
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    field + "最多 " + max + " 个字符");
        }
        return text;
    }

    private Map<Long, Long> donationCounts(List<Project> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = records.stream().map(Project::getId)
                .filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = donations.countVerifiedByProjectIds(ids);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream().collect(Collectors.toMap(
                row -> number(row, "projectId", "project_id").longValue(),
                row -> number(row, "donationCount", "donation_count").longValue(),
                (a, b) -> a));
    }

    private long donationCount(Long projectId) {
        if (projectId == null) {
            return 0;
        }
        List<Map<String, Object>> rows = donations.countVerifiedByProjectIds(List.of(projectId));
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        return number(rows.getFirst(), "donationCount", "donation_count").longValue();
    }

    private List<ProjectDonation> recentDonations(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = donations.recentVerified(projectId, RECENT_DONATIONS);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> new ProjectDonation(
                string(row, "donationNo", "donation_no"),
                string(row, "donorDisplay", "donor_display"),
                intValue(row.get("amount")),
                string(row, "message", "message"),
                toOffsetValue(row.get("verifiedAt") != null ? row.get("verifiedAt") : row.get("verified_at"))
        )).toList();
    }

    private int progressPercent(Project project) {
        int target = intValue(project.getTargetAmount());
        int raised = intValue(project.getRaisedAmount());
        if (target <= 0) {
            return 0;
        }
        long percent = (long) raised * 100 / target;
        return (int) Math.min(100, Math.max(0, percent));
    }

    private long daysRemaining(Project project, LocalDate today) {
        if (project.getEndDate() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(today, project.getEndDate()));
    }

    private Role roleOf(CurrentUser actor) {
        if (actor == null || actor.primaryRole() == null) {
            return Role.STUDENT;
        }
        try {
            return Role.valueOf(actor.primaryRole().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Role.STUDENT;
        }
    }

    private static Number number(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String string(Map<String, Object> row, String... keys) {
        Object value = value(row, keys);
        return value == null ? null : value.toString();
    }

    private static Object value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(AppConfig.APP_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetValue(Object value) {
        if (value instanceof OffsetDateTime offset) {
            return offset;
        }
        if (value instanceof LocalDateTime local) {
            return toOffset(local);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return toOffset(timestamp.toLocalDateTime());
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ValidatedProject(String name, String purpose, ProjectTargetType targetType,
                                    int targetAmount, LocalDate startDate, LocalDate endDate,
                                    List<BudgetItem> budget, String coverFileId, int budgetTotal) {
    }

    private record BudgetItem(String subject, int amount, String note) {
    }
}
