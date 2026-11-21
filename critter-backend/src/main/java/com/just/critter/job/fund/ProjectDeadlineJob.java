package com.just.critter.job.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.just.critter.domain.enums.ProjectEvent;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.Role;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.statemachine.StateMachines;
import com.just.critter.domain.statemachine.TransitionContext;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.log.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目到期扫描：每天把已过截止日、仍处于 FUNDING 的项目推进到终态。
 *
 * <p>规则与需求 §8.3 一致：达到目标金额进入 PENDING_SETTLE（可提交结项），
 * 未达到目标进入 ENDED。条件更新保证多实例幂等；只有仍为 FUNDING 的行会命中。</p>
 */
@Component
public class ProjectDeadlineJob {

    private static final Logger log = LoggerFactory.getLogger(ProjectDeadlineJob.class);
    private static final String BIZ_TYPE = "PROJECT";

    private final ProjectMapper projects;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ProjectDeadlineJob(ProjectMapper projects, AuditLogService auditLog, Clock clock) {
        this.projects = projects;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${critter.jobs.project-deadline-initial-delay-ms:60000}",
            fixedDelayString = "${critter.jobs.project-deadline-interval-ms:3600000}")
    @Transactional
    public ProjectDeadlineScanResult scan() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<Project> candidates = projects.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStatus, ProjectStatus.FUNDING)
                .eq(Project::getDeleted, 0)
                .lt(Project::getEndDate, today)
                .orderByAsc(Project::getEndDate)
                .last("LIMIT 500"));

        int pendingSettle = 0;
        int ended = 0;
        for (Project project : candidates) {
            boolean reached = project.getTargetAmount() != null
                    && project.getRaisedAmount() != null
                    && project.getRaisedAmount() >= project.getTargetAmount();
            ProjectEvent event = reached ? ProjectEvent.DEADLINE_REACHED : ProjectEvent.DEADLINE_MISSED;
            ProjectStatus next = StateMachines.project().fire(
                    ProjectStatus.FUNDING, event, TransitionContext.of(Role.SYSTEM));

            int updated = projects.update(null, new LambdaUpdateWrapper<Project>()
                    .eq(Project::getId, project.getId())
                    .eq(Project::getStatus, ProjectStatus.FUNDING)
                    .eq(Project::getDeleted, 0)
                    .set(Project::getStatus, next)
                    .set(Project::getUpdatedAt, now));
            if (updated == 0) {
                continue;
            }

            if (next == ProjectStatus.PENDING_SETTLE) {
                pendingSettle++;
            } else {
                ended++;
            }
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", ProjectStatus.FUNDING.name());
            before.put("endDate", project.getEndDate());
            before.put("raisedAmount", project.getRaisedAmount());
            before.put("targetAmount", project.getTargetAmount());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", next.name());
            after.put("scannedAt", now);
            auditLog.successAs(0L, Role.SYSTEM.name(),
                    reached ? "PROJECT_DEADLINE_REACHED" : "PROJECT_DEADLINE_MISSED",
                    BIZ_TYPE, project.getProjectNo(), before, after);
        }
        if (!candidates.isEmpty()) {
            log.info("project deadline scan candidates={} pendingSettle={} ended={}",
                    candidates.size(), pendingSettle, ended);
        }
        return new ProjectDeadlineScanResult(candidates.size(), pendingSettle, ended);
    }
}
