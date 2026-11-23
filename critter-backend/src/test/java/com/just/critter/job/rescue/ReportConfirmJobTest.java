package com.just.critter.job.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.model.Report;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.support.log.AuditLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上报到期自动确认：条件更新成功才计数，并发下更新 0 行不写审计。
 */
@ExtendWith(MockitoExtension.class)
class ReportConfirmJobTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T02:31:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ReportMapper reports;
    @Mock
    private AuditLogService auditLog;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Report.class);
    }

    @Test
    @DisplayName("到期未确认的有效结案：条件更新成功并写系统审计")
    void auto_confirm_counts_updated_rows() {
        Report report = overdueReport("RP20261121007");
        when(reports.selectList(any())).thenReturn(List.of(report));
        when(reports.update(isNull(), any())).thenReturn(1);

        int confirmed = new ReportConfirmJob(reports, auditLog, CLOCK).autoConfirm();

        assertThat(confirmed).isEqualTo(1);
        verify(auditLog).successAs(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("并发已确认：条件更新 0 行时不计入、不写审计")
    void auto_confirm_skips_row_already_taken() {
        Report report = overdueReport("RP20261121007");
        when(reports.selectList(any())).thenReturn(List.of(report));
        when(reports.update(isNull(), any())).thenReturn(0);

        int confirmed = new ReportConfirmJob(reports, auditLog, CLOCK).autoConfirm();

        assertThat(confirmed).isZero();
        verify(auditLog, never()).successAs(anyLong(), any(), any(), any(), any(), any(), any());
    }

    private Report overdueReport(String reportNo) {
        Report report = new Report();
        report.setId(5L);
        report.setReportNo(reportNo);
        report.setStatus(ReportStatus.CLOSED);
        report.setInvalid(false);
        report.setConfirmDueAt(LocalDateTime.parse("2026-11-20T10:31:00"));
        return report;
    }
}
