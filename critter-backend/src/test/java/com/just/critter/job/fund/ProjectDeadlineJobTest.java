package com.just.critter.job.fund;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.model.Project;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.log.AuditLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T4 收尾：项目到期扫描的状态选择、条件更新与审计。
 */
@ExtendWith(MockitoExtension.class)
class ProjectDeadlineJobTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZONE);

    @Mock
    private ProjectMapper projects;
    @Mock
    private AuditLogService auditLog;

    private ProjectDeadlineJob job;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 单测不会启动 MyBatis-Plus，需要手动注册实体的列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Project.class);
    }

    @BeforeEach
    void setUp() {
        job = new ProjectDeadlineJob(projects, auditLog, CLOCK);
    }

    @Test
    @DisplayName("到期且达标：进入 PENDING_SETTLE 并写 PROJECT_DEADLINE_REACHED")
    void reachedTarget_goesToPendingSettle() {
        Project project = project(1L, "PRJ-2026-001", 3_000_000, 4_200_000);
        when(projects.selectList(any())).thenReturn(List.of(project));
        when(projects.update(isNull(), any())).thenReturn(1);

        ProjectDeadlineScanResult result = job.scan();

        assertThat(result).isEqualTo(new ProjectDeadlineScanResult(1, 1, 0));
        verify(auditLog).successAs(eq(0L), eq("SYSTEM"), eq("PROJECT_DEADLINE_REACHED"),
                eq("PROJECT"), eq("PRJ-2026-001"), any(), any());
    }

    @Test
    @DisplayName("到期未达标：进入 ENDED 并写 PROJECT_DEADLINE_MISSED")
    void missedTarget_goesToEnded() {
        Project project = project(2L, "PRJ-2026-002", 5_000_000, 2_100_000);
        when(projects.selectList(any())).thenReturn(List.of(project));
        when(projects.update(isNull(), any())).thenReturn(1);

        ProjectDeadlineScanResult result = job.scan();

        assertThat(result).isEqualTo(new ProjectDeadlineScanResult(1, 0, 1));
        verify(auditLog).successAs(eq(0L), eq("SYSTEM"), eq("PROJECT_DEADLINE_MISSED"),
                eq("PROJECT"), eq("PRJ-2026-002"), any(), any());
    }

    @Test
    @DisplayName("两个候选：分别按达标/未达标推进，合计正确")
    void mixedCandidates_areCountedSeparately() {
        when(projects.selectList(any())).thenReturn(List.of(
                project(1L, "PRJ-2026-001", 3_000_000, 3_000_000),
                project(2L, "PRJ-2026-002", 5_000_000, 2_000_000)));
        when(projects.update(isNull(), any())).thenReturn(1);

        ProjectDeadlineScanResult result = job.scan();

        assertThat(result).isEqualTo(new ProjectDeadlineScanResult(2, 1, 1));
        verify(auditLog, times(2)).successAs(eq(0L), eq("SYSTEM"), any(),
                eq("PROJECT"), any(), any(), any());
    }

    @Test
    @DisplayName("并发下条件更新未命中：不重复计数、不重复审计")
    void concurrentUpdateMiss_isIdempotent() {
        when(projects.selectList(any())).thenReturn(List.of(
                project(1L, "PRJ-2026-001", 3_000_000, 4_000_000)));
        when(projects.update(isNull(), any())).thenReturn(0);

        ProjectDeadlineScanResult result = job.scan();

        assertThat(result).isEqualTo(new ProjectDeadlineScanResult(1, 0, 0));
        verify(auditLog, never()).successAs(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("无到期项目：直接返回空结果")
    void noCandidates_returnsEmpty() {
        when(projects.selectList(any())).thenReturn(List.of());

        ProjectDeadlineScanResult result = job.scan();

        assertThat(result).isEqualTo(new ProjectDeadlineScanResult(0, 0, 0));
        verify(projects, never()).update(any(), any(Wrapper.class));
    }

    private Project project(long id, String no, int target, int raised) {
        Project project = new Project();
        project.setId(id);
        project.setProjectNo(no);
        project.setStatus(ProjectStatus.FUNDING);
        project.setTargetAmount(target);
        project.setRaisedAmount(raised);
        project.setEndDate(LocalDate.of(2026, 11, 15));
        project.setDeleted(0);
        return project;
    }
}
