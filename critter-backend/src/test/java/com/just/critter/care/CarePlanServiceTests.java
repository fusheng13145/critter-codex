package com.just.critter.care;

import com.just.critter.care.plan.CarePlanCommand;
import com.just.critter.care.plan.CarePlanRepository;
import com.just.critter.care.plan.CarePlanService;
import com.just.critter.care.plan.CarePlanView;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class CarePlanServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private CarePlanRepository repository;
    private SequencePort sequences;
    private CarePlanService service;

    @BeforeEach
    void setUp() {
        repository = mock(CarePlanRepository.class);
        sequences = mock(SequencePort.class);
        service = new CarePlanService(repository, sequences, CLOCK);
    }

    @Test
    @DisplayName("按就诊记录生成计划：从 visit 反查动物并落库")
    void createsFromVisit() {
        when(repository.findVisit(5L)).thenReturn(Optional.of(
                new CarePlanRepository.VisitRef(5L, 7L, "CAT-2026-031")));
        when(repository.findAnimal("CAT-2026-031")).thenReturn(Optional.of(
                new CarePlanRepository.AnimalRef(7L, "CAT-2026-031")));
        when(sequences.next("CAREPLAN20261123", 2026)).thenReturn(1);
        when(repository.insert(eq("CP20261123001"), eq(7L), any(), eq("VACCINE"),
                any(), eq(1L))).thenReturn(19L);
        when(repository.findById(19L)).thenReturn(Optional.of(new CarePlanView(
                19L, "CP20261123001", "CAT-2026-031", "VACCINE", "狂犬疫苗", "1 支",
                LocalDate.of(2026, 12, 23), "PLANNED", 5L, null, null, null, false)));

        CarePlanView view = service.create(new CarePlanCommand(null, 5L, "VACCINE",
                "狂犬疫苗", "1 支", LocalDate.of(2026, 12, 23), null), 1L);

        assertThat(view.animalCode()).isEqualTo("CAT-2026-031");
        assertThat(view.planNo()).isEqualTo("CP20261123001");
        verify(repository).insert(eq("CP20261123001"), eq(7L), any(), eq("VACCINE"),
                any(), eq(1L));
    }

    @Test
    @DisplayName("计划类型非法：30303")
    void rejectsInvalidType() {
        assertThatThrownBy(() -> service.create(new CarePlanCommand("CAT-2026-031", null,
                "SURGERY", "绝育", null, LocalDate.of(2026, 12, 1), null), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.ENUM_INVALID.getCode()));
        verify(repository, never()).insert(any(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("没有动物编号也没有就诊记录：参数错误")
    void requiresAnimalOrVisit() {
        assertThatThrownBy(() -> service.create(new CarePlanCommand(null, null,
                "MEDICATION", "驱虫药", null, LocalDate.of(2026, 12, 1), null), 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }
}
