package com.just.critter.job.rescue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.model.Report;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.support.log.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 上报结案自动确认：工单关闭后 7 天内上报人未确认，由系统自动确认并标记 auto_confirmed。
 * 使用条件更新保证多实例幂等；无效上报不进入确认流程。
 */
@Component
public class ReportConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(ReportConfirmJob.class);

    private final ReportMapper reports;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ReportConfirmJob(ReportMapper reports, AuditLogService auditLog, Clock clock) {
        this.reports = reports;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${critter.jobs.report-confirm-initial-delay-ms:300000}",
            fixedDelayString = "${critter.jobs.report-confirm-interval-ms:3600000}")
    @Transactional
    public int autoConfirm() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<Report> candidates = reports.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, ReportStatus.CLOSED)
                .eq(Report::getInvalid, false)
                .isNull(Report::getConfirmedAt)
                .isNotNull(Report::getConfirmDueAt)
                .lt(Report::getConfirmDueAt, now)
                .orderByAsc(Report::getConfirmDueAt)
                .last("LIMIT 500"));
        int confirmed = 0;
        for (Report report : candidates) {
            int updated = reports.update(null, new LambdaUpdateWrapper<Report>()
                    .eq(Report::getId, report.getId())
                    .isNull(Report::getConfirmedAt)
                    .set(Report::getConfirmedAt, now)
                    .set(Report::getConfirmDueAt, null)
                    .set(Report::getAutoConfirmed, true)
                    .set(Report::getUpdatedAt, now));
            if (updated == 0) {
                continue;
            }
            confirmed++;
            auditLog.successAs(0L, "SYSTEM", "REPORT_AUTO_CONFIRM", "REPORT",
                    report.getReportNo(), Map.of("confirmDueAt", report.getConfirmDueAt()),
                    Map.of("confirmedAt", now, "autoConfirmed", true));
        }
        if (!candidates.isEmpty()) {
            log.info("report confirm scan candidates={} autoConfirmed={}", candidates.size(), confirmed);
        }
        return confirmed;
    }
}
