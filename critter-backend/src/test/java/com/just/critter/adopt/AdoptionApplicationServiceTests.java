package com.just.critter.adopt;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.just.critter.adopt.domain.AdoptionApplicationCommand;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.adopt.service.AdoptionApplicationService;
import com.just.critter.adopt.service.CreatedAdoption;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionApplicationServiceTests {

    private static final long USER_ID = 1001L;
    private static final long ANIMAL_ID = 11L;
    private static final String ANIMAL_CODE = "CAT-2026-031";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private AdoptionPlanRepository plans;
    private AdoptableAnimalRepository animals;
    private AdoptionRepository adoptions;
    private SequencePort sequences;
    private AdoptionApplicationService service;

    @BeforeEach
    void setUp() {
        plans = mock(AdoptionPlanRepository.class);
        animals = mock(AdoptableAnimalRepository.class);
        adoptions = mock(AdoptionRepository.class);
        sequences = mock(SequencePort.class);
        service = new AdoptionApplicationService(plans, animals, adoptions, sequences, CLOCK);
    }

    @Test
    @DisplayName("成功申请：计划快照进入待审核单，单号按日期与流水生成")
    void createsPendingAuditAdoptionWithPlanSnapshot() {
        when(plans.findById(2L)).thenReturn(Optional.of(enabledPlan()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(adoptions.findActiveAdoptionNo(ANIMAL_ID)).thenReturn(Optional.empty());
        when(adoptions.countActiveByUser(USER_ID)).thenReturn(1L);
        when(sequences.next("ADOPTION", 2026)).thenReturn(3);

        CreatedAdoption created = service.create(validCommand(), USER_ID);

        assertThat(created.adoptionNo()).isEqualTo("AD20261121003");
        assertThat(created.status()).isEqualTo(AdoptionStatus.PENDING_AUDIT);
        assertThat(created.amount()).isEqualTo(3000);
        assertThat(created.period()).isEqualTo(AdoptionPeriod.MONTH);

        ArgumentCaptor<AdoptionRecord> saved = ArgumentCaptor.forClass(AdoptionRecord.class);
        verify(adoptions).save(saved.capture());
        assertThat(saved.getValue().userId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().animalId()).isEqualTo(ANIMAL_ID);
        assertThat(saved.getValue().contact()).isEqualTo("13800000000");
        assertThat(saved.getValue().promiseAcceptedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
        assertThat(saved.getValue().remark()).isEqualTo("希望定期看照片");
    }

    @Test
    @DisplayName("动物已有生效认养时返回 30404 与原单号")
    void rejectsWhenAnimalAlreadyHasActiveAdoption() {
        when(plans.findById(2L)).thenReturn(Optional.of(enabledPlan()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(adoptions.findActiveAdoptionNo(ANIMAL_ID)).thenReturn(Optional.of("AD20261101012"));

        assertThatThrownBy(() -> service.create(validCommand(), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(30404);
                    assertThat(ex.getData()).isEqualTo(java.util.Map.of("activeAdoptionNo", "AD20261101012"));
                });

        verify(adoptions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("本人已有两只生效认养时返回 30405")
    void rejectsWhenUserHasTwoActiveAdoptions() {
        when(plans.findById(2L)).thenReturn(Optional.of(enabledPlan()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.RELEASED)));
        when(adoptions.findActiveAdoptionNo(ANIMAL_ID)).thenReturn(Optional.empty());
        when(adoptions.countActiveByUser(USER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(validCommand(), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30405));

        verify(adoptions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("动物不在可认养状态时返回 30406")
    void rejectsNonAdoptableAnimalStatus() {
        when(plans.findById(2L)).thenReturn(Optional.of(enabledPlan()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.MISSING)));

        assertThatThrownBy(() -> service.create(validCommand(), USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30406));

        verify(adoptions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("未接受认养承诺时返回 30301")
    void rejectsWhenPromiseIsNotAccepted() {
        AdoptionApplicationCommand command = new AdoptionApplicationCommand(
                ANIMAL_CODE, 2L, false, "13800000000", "希望定期看照片");

        assertThatThrownBy(() -> service.create(command, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verify(plans, org.mockito.Mockito.never()).findById(anyLong());
        verify(adoptions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("联系方式为空时返回 30301")
    void rejectsWhenContactIsBlank() {
        AdoptionApplicationCommand command = new AdoptionApplicationCommand(
                ANIMAL_CODE, 2L, true, "   ", null);

        assertThatThrownBy(() -> service.create(command, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    private AdoptionApplicationCommand validCommand() {
        return new AdoptionApplicationCommand(
                ANIMAL_CODE, 2L, true, " 13800000000 ", " 希望定期看照片 ");
    }

    private AdoptionPlan enabledPlan() {
        return new AdoptionPlan(2L, "月度守护", AdoptionPeriod.MONTH, 3000, "每月照片", true);
    }
}
