package com.just.critter.adopt;

import com.just.critter.adopt.handover.HandoverCommand;
import com.just.critter.adopt.handover.HandoverRepository;
import com.just.critter.adopt.handover.HandoverService;
import com.just.critter.adopt.handover.HandoverView;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class HandoverServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private HandoverRepository repository;
    private SequencePort sequences;
    private HandoverService service;

    @BeforeEach
    void setUp() {
        repository = mock(HandoverRepository.class);
        sequences = mock(SequencePort.class);
        service = new HandoverService(repository, sequences, CLOCK);
    }

    @Test
    @DisplayName("缺少监护人同意：返回 30409，且不创建交接单")
    void rejectsWithoutGuardianConsent() {
        HandoverCommand command = new HandoverCommand("CAT-2026-031", "纪远",
                "13800000000", false, "独立住房，已封窗", null);

        assertThatThrownBy(() -> service.create(command, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.ADOPTION_MATERIAL_MISSING.getCode()));
        verify(repository, never()).insertHandover(any(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("材料齐全：生成 HO 编号与 7/30/90 天三次回访")
    void createsThreeFollowups() {
        when(sequences.next("HANDOVER20261123", 2026)).thenReturn(1);
        when(repository.findAnimal("CAT-2026-031"))
                .thenReturn(Optional.of(new HandoverRepository.AnimalRef(7L, "CAT-2026-031")));
        when(repository.insertHandover(eq("HO20261123001"), eq(7L), eq("CAT-2026-031"),
                any(), any(), eq(1L))).thenReturn(31L);
        HandoverView view = new HandoverView(31L, "HO20261123001", 7L, "CAT-2026-031",
                "纪远", "13800000000", true, "独立住房，已封窗", "ACTIVE",
                LocalDateTime.of(2026, 11, 23, 10, 0), 0, 3);
        when(repository.findByNo("HO20261123001")).thenReturn(Optional.of(view));
        when(repository.findFollowups(31L)).thenReturn(List.of());

        var detail = service.create(new HandoverCommand("CAT-2026-031", "纪远",
                "13800000000", true, "独立住房，已封窗", null), 1L);

        assertThat(detail.handover().handoverNo()).isEqualTo("HO20261123001");
        verify(repository).insertFollowup(eq(31L), eq(1), eq(LocalDate.of(2026, 11, 30)), eq(1L));
        verify(repository).insertFollowup(eq(31L), eq(2), eq(LocalDate.of(2026, 12, 23)), eq(1L));
        verify(repository).insertFollowup(eq(31L), eq(3), eq(LocalDate.of(2027, 2, 21)), eq(1L));
    }

    @Test
    @DisplayName("回访结果非法：枚举错误 30303")
    void rejectsInvalidFollowupResult() {
        when(repository.findByNo("HO20261123001")).thenReturn(Optional.of(
                new HandoverView(31L, "HO20261123001", 7L, "CAT-2026-031", "纪远",
                        "13800000000", true, "独立住房", "ACTIVE",
                        LocalDateTime.of(2026, 11, 23, 10, 0), 0, 3)));
        when(repository.findFollowup(31L, 1)).thenReturn(Optional.of(
                new com.just.critter.adopt.handover.FollowupView(51L, 1,
                        LocalDate.of(2026, 11, 30), "PENDING", null, null, null, false)));

        assertThatThrownBy(() -> service.complete("HO20261123001", 1,
                new com.just.critter.adopt.handover.FollowupCompleteCommand("BAD", null), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.ENUM_INVALID.getCode()));
        verify(repository, never()).completeFollowup(anyLong(), any(), any(), any(), anyLong());
    }
}
