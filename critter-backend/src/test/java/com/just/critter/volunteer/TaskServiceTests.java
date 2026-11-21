package com.just.critter.volunteer;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.volunteer.repository.TaskRepository;
import com.just.critter.volunteer.service.TaskCheckinCommand;
import com.just.critter.volunteer.service.TaskCheckinView;
import com.just.critter.volunteer.service.TaskCommand;
import com.just.critter.volunteer.service.TaskRecord;
import com.just.critter.volunteer.service.TaskService;
import com.just.critter.volunteer.service.TaskSignupRecord;
import com.just.critter.volunteer.service.TaskSignupView;
import com.just.critter.volunteer.service.TaskView;
import com.just.critter.volunteer.service.VolunteerRecord;
import com.just.critter.volunteer.service.VolunteerTaskView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final long USER_ID = 1001L;

    private TaskRepository repository;
    private SequencePort sequences;
    private AuditLogService audit;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        sequences = mock(SequencePort.class);
        audit = mock(AuditLogService.class);
        service = new TaskService(repository, sequences, audit, CLOCK);
    }

    @Test
    @DisplayName("发布任务：生成 TASK 编号，初始 OPEN 并写审计")
    void createsTask() {
        when(sequences.next("TASK20261121", 2026)).thenReturn(1);
        when(repository.pointExists(3L)).thenReturn(true);
        TaskRecord created = record(4L, "TASK20261121001", "周末投喂", "FEED", 2, 0, "OPEN");
        when(repository.insert(any())).thenReturn(created);

        TaskView view = service.create(new TaskCommand(
                " 周末投喂 ", "FEED", 3L, LocalDateTime.of(2026, 11, 22, 9, 0),
                2, new BigDecimal("2.5"), " 带好手套 "), 1L);

        assertThat(view.taskNo()).isEqualTo("TASK20261121001");
        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.joined()).isZero();
        verify(audit).success(eq("TASK_CREATE"), eq("TASK"), eq("4"), eq(null), eq(created));
    }

    @Test
    @DisplayName("发布任务：类型非法返回 30303")
    void rejectsInvalidType() {
        assertThatThrownBy(() -> service.create(new TaskCommand(
                "任务", "PARTY", 1L, LocalDateTime.now().plusDays(1), 2,
                new BigDecimal("2.0"), null), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));
    }

    @Test
    @DisplayName("发布任务：名额与时长非法返回 30301")
    void rejectsInvalidNumbers() {
        assertThatThrownBy(() -> service.create(new TaskCommand(
                "任务", "FEED", 1L, LocalDateTime.now().plusDays(1), 0,
                new BigDecimal("2.0"), null), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.create(new TaskCommand(
                "任务", "FEED", 1L, LocalDateTime.now().plusDays(1), 2,
                new BigDecimal("0"), null), 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("报名：活跃志愿者加入未满任务，名额 +1")
    void joinsTask() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", new BigDecimal("8.0"))));
        when(repository.findTaskForUpdate(4L)).thenReturn(Optional.of(
                record(4L, "TASK20261121001", "周末投喂", "FEED", 4, 2, "OPEN")));
        when(repository.findSignup(4L, USER_ID)).thenReturn(Optional.empty());
        when(repository.insertSignup(any())).thenReturn(21L);
        when(repository.incrementJoined(eq(4L), any())).thenReturn(1);

        TaskView view = service.join(4L, USER_ID);

        assertThat(view.joinedByMe()).isTrue();
        assertThat(view.joined()).isEqualTo(3);
        verify(repository).incrementJoined(eq(4L), any());
        verify(repository, never()).markFull(anyLong(), any());
    }

    @Test
    @DisplayName("报名：未审核通过的志愿者返回 20003")
    void rejectsInactiveVolunteer() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("PENDING", BigDecimal.ZERO)));

        assertThatThrownBy(() -> service.join(4L, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(20003));
    }

    @Test
    @DisplayName("报名：任务已满返回 30402")
    void rejectsFullTask() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", BigDecimal.ZERO)));
        when(repository.findTaskForUpdate(4L)).thenReturn(Optional.of(
                record(4L, "TASK20261121001", "周末投喂", "FEED", 2, 2, "FULL")));

        assertThatThrownBy(() -> service.join(4L, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));
    }

    @Test
    @DisplayName("报名：重复报名返回 30403")
    void rejectsDuplicateSignup() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", BigDecimal.ZERO)));
        when(repository.findTaskForUpdate(4L)).thenReturn(Optional.of(
                record(4L, "TASK20261121001", "周末投喂", "FEED", 4, 2, "OPEN")));
        when(repository.findSignup(4L, USER_ID)).thenReturn(Optional.of(
                new TaskSignupRecord(21L, 4L, USER_ID, LocalDateTime.now(), null, null, BigDecimal.ZERO, false)));

        assertThatThrownBy(() -> service.join(4L, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30403));
    }

    @Test
    @DisplayName("打卡：写入打卡时间、照片与服务时长，累计到志愿者总时长")
    void checksIn() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", new BigDecimal("8.0"))));
        when(repository.findSignup(4L, USER_ID)).thenReturn(Optional.of(
                new TaskSignupRecord(21L, 4L, USER_ID,
                        LocalDateTime.of(2026, 11, 21, 10, 0), null, null, BigDecimal.ZERO, false)));
        when(repository.findTask(4L)).thenReturn(Optional.of(
                record(4L, "TASK20261121001", "周末投喂", "FEED", 3, 3, "FULL")));
        when(repository.checkin(eq(4L), eq(USER_ID), any(), eq("file-1"),
                eq(new BigDecimal("3.0")))).thenReturn(1);

        TaskCheckinView view = service.checkin(4L, new TaskCheckinCommand("  file-1  "), USER_ID);

        assertThat(view.hours()).isEqualByComparingTo("3.0");
        assertThat(view.totalHours()).isEqualByComparingTo("11.0");
        verify(repository).addVolunteerHours(eq(USER_ID), eq(new BigDecimal("3.0")), any());
    }

    @Test
    @DisplayName("打卡：缺少照片返回 30301")
    void rejectsMissingPhoto() {
        assertThatThrownBy(() -> service.checkin(4L, new TaskCheckinCommand("  "), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    @Test
    @DisplayName("打卡：未报名返回 30410")
    void rejectsCheckinWithoutSignup() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", BigDecimal.ZERO)));
        when(repository.findSignup(4L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkin(4L, new TaskCheckinCommand("file-1"), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("我的任务：返回志愿者状态、总时长与报名记录")
    void listsMine() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", new BigDecimal("8.0"))));
        when(repository.countMyTasks(USER_ID)).thenReturn(1L);
        when(repository.findMyTasks(USER_ID, 0, 20)).thenReturn(List.of(
                new TaskSignupView(21L, 4L, "TASK20261121001", "周末投喂",
                        LocalDateTime.of(2026, 11, 22, 9, 0),
                        LocalDateTime.of(2026, 11, 21, 10, 0), null,
                        new BigDecimal("0.0"), "FULL", true, false)));

        VolunteerTaskView view = service.mine(USER_ID, 1, 20);

        assertThat(view.volunteerStatus()).isEqualTo("ACTIVE");
        assertThat(view.totalHours()).isEqualByComparingTo("8.0");
        assertThat(view.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("报名：占满最后一个名额时返回 FULL")
    void joinsLastSeatReturnsFull() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new VolunteerRecord("ACTIVE", BigDecimal.ZERO)));
        when(repository.findTaskForUpdate(4L)).thenReturn(Optional.of(
                record(4L, "TASK20261121001", "周末投喂", "FEED", 4, 3, "OPEN")));
        when(repository.findSignup(4L, USER_ID)).thenReturn(Optional.empty());
        when(repository.insertSignup(any())).thenReturn(31L);
        when(repository.incrementJoined(eq(4L), any())).thenReturn(1);

        TaskView view = service.join(4L, USER_ID);

        assertThat(view.joined()).isEqualTo(4);
        assertThat(view.status()).isEqualTo("FULL");
        verify(repository).markFull(eq(4L), any());
    }

    private TaskRecord record(long id, String no, String title, String type,
                              int quota, int joined, String status) {
        return new TaskRecord(id, no, title, type, "说明", 3L, "一号点位",
                LocalDateTime.of(2026, 11, 22, 9, 0), quota, joined,
                new BigDecimal("3.0"), status, null, null, false);
    }
}
