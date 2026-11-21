package com.just.critter.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.domain.enums.ProjectEvent;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.ProjectSettlement;
import com.just.critter.domain.model.ProjectSettlementNote;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.ExpenseMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.mapper.ProjectSettlementMapper;
import com.just.critter.mapper.ProjectSettlementNoteMapper;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * T3.4 结项公示：项目达到目标或到达截止日后进入待结项，管理员提交结项报告并公示。
 * 结项前必须清空所有待复核支出，公示金额取“已筹”和“已复核支出”。
 */
@Service
public class ProjectSettlementService {

    private static final int MIN_REPORT = 20;
    private static final int MAX_REPORT = 10000;
    private static final int MAX_BALANCE_TO = 255;
    private static final int MAX_ATTACHMENTS = 10;
    private static final int MIN_NOTE = 2;
    private static final int MAX_NOTE = 2000;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ProjectMapper projects;
    private final ExpenseMapper expenses;
    private final ProjectSettlementMapper settlements;
    private final ProjectSettlementNoteMapper notes;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ProjectSettlementService(ProjectMapper projects,
                                    ExpenseMapper expenses,
                                    ProjectSettlementMapper settlements,
                                    ProjectSettlementNoteMapper notes,
                                    AuditLogService auditLog,
                                    Clock clock) {
        this.projects = projects;
        this.expenses = expenses;
        this.settlements = settlements;
        this.notes = notes;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public ProjectSettlementView settle(String projectNo, SettleProjectCommand command, CurrentUser actor) {
        if (actor == null || !actor.hasRole("ANIMAL_ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "仅动物管理员可提交结项");
        }
        Project project = projects.selectByProjectNo(projectNo);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        project = projects.selectByIdForUpdate(project.getId());
        if (settlements.selectByProjectId(project.getId()) != null) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID, "项目已结项公示，不可重复提交");
        }
        if (expenses.countPendingByProjectId(project.getId()) > 0) {
            throw new BusinessException(ErrorCode.UNREVIEWED_EXPENSE_EXISTS, "存在待复核支出，不能结项");
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        ProjectStatus before = project.getStatus();
        ProjectStatus current = before;
        if (current == ProjectStatus.FUNDING) {
            ProjectEvent trigger = reachedTarget(project) ? ProjectEvent.TARGET_REACHED
                    : deadlineReached(project) ? ProjectEvent.DEADLINE_REACHED : null;
            if (trigger == null) {
                throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID,
                        "项目未达到目标且未到截止日，暂不能结项");
            }
            current = StateMachines.project().fire(current, trigger, context());
        }
        if (current != ProjectStatus.PENDING_SETTLE) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID, "仅待结项项目可提交结项报告");
        }
        ProjectStatus target = StateMachines.project().fire(current, ProjectEvent.SETTLE, context());

        String reportText = command == null ? null : trimToNull(command.reportText());
        if (reportText == null || reportText.length() < MIN_REPORT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结项报告至少 " + MIN_REPORT + " 个字");
        }
        if (reportText.length() > MAX_REPORT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结项报告最多 " + MAX_REPORT + " 个字");
        }
        String balanceTo = command == null ? null : trimToNull(command.balanceTo());
        if (balanceTo == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写结余去向");
        }
        if (balanceTo.length() > MAX_BALANCE_TO) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结余去向最多 " + MAX_BALANCE_TO + " 个字");
        }
        List<String> attachments = cleanAttachments(command == null ? null : command.attachments());

        int raised = project.getRaisedAmount() == null ? 0 : project.getRaisedAmount();
        int spent = expenses.sumApprovedByProjectId(project.getId());
        if (spent > raised) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "已复核支出超过已筹金额，请先核对账目");
        }

        ProjectSettlement settlement = new ProjectSettlement();
        settlement.setProjectId(project.getId());
        settlement.setReportText(reportText);
        settlement.setTotalRaised(raised);
        settlement.setTotalSpent(spent);
        settlement.setBalanceTo(balanceTo);
        settlement.setAttachments(attachments);
        settlement.setPublishedAt(now);
        settlement.setCreatedAt(now);
        settlement.setUpdatedAt(now);
        settlement.setCreatedBy(actor.id());
        settlements.insert(settlement);

        project.setStatus(target);
        project.setSpentAmount(spent);
        project.setUpdatedAt(now);
        projects.updateById(project);

        auditLog.success("PROJECT_SETTLE", "PROJECT", project.getProjectNo(),
                Map.of("status", before.name(), "raised", String.valueOf(raised)),
                Map.of("status", target.name(), "spent", String.valueOf(spent),
                        "balanceTo", balanceTo));
        return view(project, settlement);
    }

    public ProjectSettlementView detail(String projectNo) {
        Project project = projects.selectByProjectNo(projectNo);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        ProjectSettlement settlement = settlements.selectByProjectId(project.getId());
        if (settlement == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目尚未发布结项公示");
        }
        return view(project, settlement);
    }

    /**
     * 公示发布后只能追加说明，正文、金额、附件与去向都不可再改。
     */
    @Transactional
    public ProjectSettlementView appendNote(String projectNo, AppendSettlementNoteCommand command,
                                            CurrentUser actor) {
        if (actor == null || !actor.hasRole("ANIMAL_ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "仅动物管理员可追加公示说明");
        }
        String noteText = command == null ? null : trimToNull(command.note());
        if (noteText == null || noteText.length() < MIN_NOTE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "追加说明至少 " + MIN_NOTE + " 个字");
        }
        if (noteText.length() > MAX_NOTE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "追加说明最多 " + MAX_NOTE + " 个字");
        }
        Project project = projects.selectByProjectNo(projectNo);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        ProjectSettlement settlement = settlements.selectByProjectId(project.getId());
        if (settlement == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目尚未发布结项公示");
        }
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        ProjectSettlementNote note = new ProjectSettlementNote();
        note.setSettlementId(settlement.getId());
        note.setNote(noteText);
        note.setOperatorId(actor.id());
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        note.setCreatedBy(actor.id());
        notes.insert(note);
        auditLog.success("PROJECT_SETTLEMENT_APPEND", "PROJECT", project.getProjectNo(),
                Map.of("settlementId", String.valueOf(settlement.getId())),
                Map.of("noteId", String.valueOf(note.getId()), "length", String.valueOf(noteText.length())));
        return view(project, settlement);
    }

    private ProjectSettlementView view(Project project, ProjectSettlement settlement) {
        int balance = settlement.getTotalRaised() - settlement.getTotalSpent();
        List<SettlementNoteView> appendNotes = notes.selectList(
                        new LambdaQueryWrapper<ProjectSettlementNote>()
                                .eq(ProjectSettlementNote::getSettlementId, settlement.getId())
                                .orderByAsc(ProjectSettlementNote::getCreatedAt)
                                .orderByAsc(ProjectSettlementNote::getId))
                .stream()
                .map(row -> new SettlementNoteView(row.getNote(), row.getOperatorId(),
                        toOffset(row.getCreatedAt())))
                .toList();
        return new ProjectSettlementView(project.getProjectNo(), project.getName(),
                settlement.getReportText(), settlement.getTotalRaised(), settlement.getTotalSpent(),
                balance, settlement.getBalanceTo(),
                settlement.getAttachments() == null ? List.of() : settlement.getAttachments(),
                toOffset(settlement.getPublishedAt()), appendNotes);
    }

    private boolean reachedTarget(Project project) {
        return project.getTargetAmount() != null && project.getRaisedAmount() != null
                && project.getRaisedAmount() >= project.getTargetAmount();
    }

    private boolean deadlineReached(Project project) {
        return project.getEndDate() != null && !project.getEndDate().isAfter(LocalDate.now(clock));
    }

    private TransitionContext context() {
        return TransitionContext.of(Role.ANIMAL_ADMIN);
    }

    private List<String> cleanAttachments(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结项附件最多 " + MAX_ATTACHMENTS + " 个");
        }
        return raw.stream().map(this::requireFileId).toList();
    }

    private String requireFileId(String value) {
        String id = trimToNull(value);
        if (id == null || id.length() > 24) {
            throw new BusinessException(ErrorCode.FORMAT_INVALID, "附件文件编号不合法");
        }
        return id;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }
}
