package com.just.critter.volunteer;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.volunteer.shift.AbsenceResult;
import com.just.critter.volunteer.shift.AppealView;
import com.just.critter.volunteer.shift.ShiftRepository;
import com.just.critter.volunteer.shift.ShiftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

class ShiftServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final long USER_ID = 3001L;

    private ShiftRepository repository;
    private SequencePort sequences;
    private MessageRepository messages;
    private ShiftService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShiftRepository.class);
        sequences = mock(SequencePort.class);
        messages = mock(MessageRepository.class);
        service = new ShiftService(repository, sequences, messages, CLOCK);
    }

    @Test
    @DisplayName("暂停接单：报名直接 403，不进入锁班次")
    void suspendedVolunteerCannotSignup() {
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new ShiftRepository.VolunteerState("SUSPENDED", 3,
                        LocalDateTime.of(2026, 11, 20, 10, 0))));

        assertThatThrownBy(() -> service.signup(8L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION.getCode()));
        verify(repository, never()).findShiftForUpdate(anyLong());
    }

    @Test
    @DisplayName("第三次缺勤：写缺勤、累计到 3、暂停并投递站内消息")
    void thirdAbsenceSuspendsVolunteer() {
        when(repository.findSignupForUpdate(8L, USER_ID)).thenReturn(Optional.of(
                new ShiftRepository.SignupRow(21L, null, false)));
        when(repository.markAbsent(21L)).thenReturn(1);
        when(repository.findVolunteer(USER_ID)).thenReturn(Optional.of(
                new ShiftRepository.VolunteerState("ACTIVE", 3, null)));
        when(repository.suspend(eq(USER_ID), any())).thenReturn(1);

        AbsenceResult result = service.markAbsent(8L, USER_ID, 1L);

        assertThat(result.absenceStreak()).isEqualTo(3);
        assertThat(result.suspended()).isTrue();
        verify(repository).incrementAbsenceStreak(USER_ID);
        verify(repository).suspend(eq(USER_ID), any());
        verify(messages).insert(any(MessageWrite.class));
    }

    @Test
    @DisplayName("申诉通过：恢复 ACTIVE 并清零连续缺勤")
    void approvedAppealReactivatesVolunteer() {
        when(repository.findAppealForUpdate(5L)).thenReturn(Optional.of(
                new ShiftRepository.AppealRow(5L, USER_ID, "PENDING")));
        when(repository.reviewAppeal(eq(5L), eq("APPROVED"), eq("情况属实"), eq(1L), any()))
                .thenReturn(1);
        when(repository.reactivate(USER_ID)).thenReturn(1);
        when(repository.findAppealById(5L)).thenReturn(Optional.of(
                new AppealView(5L, "纪远", "生病住院", "APPROVED", "情况属实",
                        LocalDateTime.of(2026, 11, 23, 10, 0),
                        LocalDateTime.of(2026, 11, 22, 10, 0))));

        AppealView view = service.reviewAppeal(5L, "APPROVE", "情况属实", 1L);

        assertThat(view.status()).isEqualTo("APPROVED");
        verify(repository).reactivate(USER_ID);
    }
}
