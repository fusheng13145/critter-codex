package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.adopt.service.AdoptionLifecycleService;
import com.just.critter.adopt.service.AdoptionTermination;
import com.just.critter.adopt.service.TerminatedAdoption;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageType;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionLifecycleServiceTests {

    private static final String ADOPTION_NO = "AD20261121003";
    private static final long ADOPTION_ID = 101L;
    private static final long ANIMAL_ID = 11L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private AdoptionRepository adoptions;
    private AdoptableAnimalRepository animals;
    private MessageRepository messages;
    private AdoptionLifecycleService service;

    @BeforeEach
    void setUp() {
        adoptions = mock(AdoptionRepository.class);
        animals = mock(AdoptableAnimalRepository.class);
        messages = mock(MessageRepository.class);
        service = new AdoptionLifecycleService(adoptions, animals, messages, CLOCK);
    }

    @Test
    @DisplayName("终止生效认养：写明原因、结束日期，并把动物恢复为在校园")
    void terminatesActiveAdoptionAndReleasesAnimal() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.ACTIVE)));
        when(adoptions.terminate(any())).thenReturn(1);
        when(animals.markOnCampus(ANIMAL_ID)).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        TerminatedAdoption result = service.terminate(ADOPTION_NO, "认养人申请终止");

        assertThat(result.status()).isEqualTo(AdoptionStatus.TERMINATED);
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(result.animalStatus()).isEqualTo("ON_CAMPUS");

        ArgumentCaptor<AdoptionTermination> captor = ArgumentCaptor.forClass(AdoptionTermination.class);
        verify(adoptions).terminate(captor.capture());
        assertThat(captor.getValue().adoptionId()).isEqualTo(ADOPTION_ID);
        assertThat(captor.getValue().reason()).isEqualTo("认养人申请终止");
        assertThat(captor.getValue().endDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        verify(animals).markOnCampus(ANIMAL_ID);
        ArgumentCaptor<MessageWrite> notice = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(notice.capture());
        assertThat(notice.getValue().receiverId()).isEqualTo(1001L);
        assertThat(notice.getValue().type()).isEqualTo(MessageType.APPROVAL_RESULT.name());
        assertThat(notice.getValue().title()).contains("终止").contains("认养人申请终止");
        assertThat(notice.getValue().bizId()).isEqualTo(ADOPTION_NO);
    }

    @Test
    @DisplayName("终止原因少于五字返回 30301 且不锁认养单")
    void rejectsShortTerminationReason() {
        assertThatThrownBy(() -> service.terminate(ADOPTION_NO, "不租了"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        verify(adoptions, never()).findByNoForUpdate(any());
    }

    @Test
    @DisplayName("非生效关系不得终止，返回 30402")
    void rejectsNonActiveAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> service.terminate(ADOPTION_NO, "认养人申请终止"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));
        verify(adoptions, never()).terminate(any());
        verify(animals, never()).markOnCampus(anyLong());
    }

    @Test
    @DisplayName("认养单不存在返回 30410")
    void rejectsMissingAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.terminate(ADOPTION_NO, "认养人申请终止"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    private AdoptionRecord record(AdoptionStatus status) {
        return new AdoptionRecord(
                ADOPTION_ID, ADOPTION_NO, 1001L, ANIMAL_ID, "CAT-2026-031", 2L,
                3000, AdoptionPeriod.MONTH, status, "13800000000", "希望定期看照片",
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }
}
