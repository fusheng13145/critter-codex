package com.just.critter.adopt;

import com.just.critter.adopt.benefit.AdoptionBenefitRepository;
import com.just.critter.adopt.benefit.AdoptionBenefitService;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionBenefitServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final long OWNER_ID = 9001L;

    private AdoptionBenefitRepository repository;
    private AdoptionBenefitService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdoptionBenefitRepository.class);
        service = new AdoptionBenefitService(repository, CLOCK);
    }

    @Test
    @DisplayName("生效认养：本人可读专属动态与探视记录")
    void activeOwnerCanReadBenefits() {
        var scope = new AdoptionBenefitRepository.Scope(11L, OWNER_ID, "AD20261123001",
                "CAT-2026-031", "糯米", LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1), "ACTIVE");
        when(repository.findScope("AD20261123001")).thenReturn(Optional.of(scope));
        when(repository.findPosts(11L)).thenReturn(List.of());
        when(repository.findVisits(11L)).thenReturn(List.of());

        var view = service.view("AD20261123001", OWNER_ID);

        assertThat(view.animalCode()).isEqualTo("CAT-2026-031");
        assertThat(view.status()).isEqualTo("ACTIVE");
        verify(repository).findPosts(11L);
        verify(repository).findVisits(11L);
    }

    @Test
    @DisplayName("关系终止后专属页返回 403（20002），不读动态与探视")
    void terminatedRelationReturnsForbidden() {
        var scope = new AdoptionBenefitRepository.Scope(11L, OWNER_ID, "AD20261123001",
                "CAT-2026-031", "糯米", LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1), "TERMINATED");
        when(repository.findScope("AD20261123001")).thenReturn(Optional.of(scope));

        assertThatThrownBy(() -> service.view("AD20261123001", OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION.getCode()));
        verify(repository, never()).findPosts(anyLong());
    }

    @Test
    @DisplayName("非认养人本人访问同样 403")
    void nonOwnerReturnsForbidden() {
        var scope = new AdoptionBenefitRepository.Scope(11L, OWNER_ID, "AD20261123001",
                "CAT-2026-031", "糯米", LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1), "ACTIVE");
        when(repository.findScope("AD20261123001")).thenReturn(Optional.of(scope));

        assertThatThrownBy(() -> service.view("AD20261123001", 1234L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.NO_PERMISSION.getCode()));
    }

    @Test
    @DisplayName("探视预约：过去日期被拒绝")
    void rejectsPastVisitDate() {
        var scope = new AdoptionBenefitRepository.Scope(11L, OWNER_ID, "AD20261123001",
                "CAT-2026-031", "糯米", LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 11, 1), "ACTIVE");
        when(repository.findScope("AD20261123001")).thenReturn(Optional.of(scope));

        assertThatThrownBy(() -> service.bookVisit("AD20261123001", OWNER_ID,
                new com.just.critter.adopt.benefit.VisitBookingCommand(
                        LocalDate.of(2026, 11, 22), "09:00-11:00", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
    }
}
