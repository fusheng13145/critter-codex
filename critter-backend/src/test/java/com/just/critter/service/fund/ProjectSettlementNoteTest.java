package com.just.critter.service.fund;

import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.ProjectSettlement;
import com.just.critter.domain.model.ProjectSettlementNote;
import com.just.critter.mapper.ExpenseMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.mapper.ProjectSettlementMapper;
import com.just.critter.mapper.ProjectSettlementNoteMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 结项公示追加说明：只能追加，不能改正文，只有管理员可操作。
 */
@ExtendWith(MockitoExtension.class)
class ProjectSettlementNoteTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T02:31:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProjectSettlementNote.class);
    }

    @Mock
    private ProjectMapper projects;
    @Mock
    private ExpenseMapper expenses;
    @Mock
    private ProjectSettlementMapper settlements;
    @Mock
    private ProjectSettlementNoteMapper notes;
    @Mock
    private AuditLogService auditLog;

    @Test
    @DisplayName("追加说明：写入 note 表并以 appendNotes 返回，公示正文保持不变")
    void append_note_keeps_original_report() {
        Project project = new Project();
        project.setId(21L);
        project.setProjectNo("PRJ-2026-001");
        project.setName("校园流浪猫绝育计划");
        ProjectSettlement settlement = new ProjectSettlement();
        settlement.setId(31L);
        settlement.setReportText("原公示正文");
        settlement.setTotalRaised(100_000);
        settlement.setTotalSpent(80_000);
        settlement.setBalanceTo("转入下一期救助基金");
        settlement.setPublishedAt(LocalDateTime.parse("2026-11-20T09:30:00"));
        ProjectSettlementNote note = new ProjectSettlementNote();
        note.setId(41L);
        note.setSettlementId(31L);
        note.setNote("补充：新增 2 只幼猫已安排领养");
        note.setOperatorId(9001L);
        note.setCreatedAt(LocalDateTime.parse("2026-11-21T10:31:00"));
        when(projects.selectByProjectNo("PRJ-2026-001")).thenReturn(project);
        when(settlements.selectByProjectId(21L)).thenReturn(settlement);
        when(notes.insert(any(ProjectSettlementNote.class))).thenReturn(1);
        when(notes.selectList(any())).thenReturn(List.of(note));

        ProjectSettlementService service = new ProjectSettlementService(
                projects, expenses, settlements, notes, auditLog, CLOCK);
        ProjectSettlementView view = service.appendNote("PRJ-2026-001",
                new AppendSettlementNoteCommand("补充：新增 2 只幼猫已安排领养"), admin());

        assertThat(view.reportText()).isEqualTo("原公示正文");
        assertThat(view.appendNotes()).singleElement().satisfies(item ->
                assertThat(item.note()).isEqualTo("补充：新增 2 只幼猫已安排领养"));
        ArgumentCaptor<ProjectSettlementNote> captor = ArgumentCaptor.forClass(ProjectSettlementNote.class);
        org.mockito.Mockito.verify(notes).insert(captor.capture());
        assertThat(captor.getValue().getSettlementId()).isEqualTo(31L);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("非管理员或未公示项目：拒绝追加")
    void append_rejects_non_admin_and_unpublished() {
        ProjectSettlementService service = new ProjectSettlementService(
                projects, expenses, settlements, notes, auditLog, CLOCK);
        CurrentUser student = new CurrentUser(2002L, "2023020002", "学生", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jwt-2", null);
        assertThatThrownBy(() -> service.appendNote("PRJ-2026-001",
                new AppendSettlementNoteCommand("补充说明内容"), student))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(com.just.critter.support.exception.ErrorCode.ROLE_MISMATCH.getCode()));

        Project project = new Project();
        project.setId(21L);
        project.setProjectNo("PRJ-2026-001");
        when(projects.selectByProjectNo("PRJ-2026-001")).thenReturn(project);
        when(settlements.selectByProjectId(21L)).thenReturn(null);
        assertThatThrownBy(() -> service.appendNote("PRJ-2026-001",
                new AppendSettlementNoteCommand("补充说明内容"), admin()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(com.just.critter.support.exception.ErrorCode.RECORD_NOT_FOUND.getCode()));
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jwt-admin", null);
    }
}
