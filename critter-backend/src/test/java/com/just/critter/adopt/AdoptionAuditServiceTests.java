package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.domain.AdoptionAuditCommand;
import com.just.critter.adopt.domain.AdoptionDecision;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.adopt.service.AuditedAdoption;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageType;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionAuditServiceTests {

    private static final String ADOPTION_NO = "AD20261121003";
    private static final long AUDITOR_ID = 9001L;
    private static final long ANIMAL_ID = 11L;
    private static final String ANIMAL_CODE = "CAT-2026-031";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private AdoptionRepository adoptions;
    private AdoptableAnimalRepository animals;
    private MessageRepository messages;
    private AdoptionAuditService service;

    @BeforeEach
    void setUp() {
        adoptions = mock(AdoptionRepository.class);
        animals = mock(AdoptableAnimalRepository.class);
        messages = mock(MessageRepository.class);
        service = new AdoptionAuditService(adoptions, animals, messages, CLOCK);
    }

    @Test
    @DisplayName("审核通过：锁定动物并转待缴费，按七天生成缴费截止时间")
    void approvesToPendingPaymentAndComputesDeadline() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO)).thenReturn(Optional.of(pendingAuditAdoption()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(adoptions.findBlockingAdoptionNo(ANIMAL_ID, ADOPTION_NO)).thenReturn(Optional.empty());
        when(adoptions.updateAudit(eq(ADOPTION_NO), eq(AdoptionStatus.PENDING_PAYMENT), eq(AUDITOR_ID),
                eq("学籍与数量核对通过"), eq(3000), any(LocalDateTime.class))).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        AuditedAdoption result = service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, "学籍与数量核对通过", null), AUDITOR_ID);

        assertThat(result.adoptionNo()).isEqualTo(ADOPTION_NO);
        assertThat(result.status()).isEqualTo(AdoptionStatus.PENDING_PAYMENT);
        assertThat(result.payDeadline()).isEqualTo(OffsetDateTime.parse("2026-11-28T12:00:00+08:00"));
        verify(adoptions).updateAudit(eq(ADOPTION_NO), eq(AdoptionStatus.PENDING_PAYMENT), eq(AUDITOR_ID),
                eq("学籍与数量核对通过"), eq(3000), eq(LocalDateTime.of(2026, 11, 21, 12, 0)));
        ArgumentCaptor<MessageWrite> notice = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(notice.capture());
        assertThat(notice.getValue().receiverId()).isEqualTo(1001L);
        assertThat(notice.getValue().type()).isEqualTo(MessageType.APPROVAL_RESULT.name());
        assertThat(notice.getValue().title()).contains("通过");
        assertThat(notice.getValue().bizType()).isEqualTo("ADOPTION");
        assertThat(notice.getValue().bizId()).isEqualTo(ADOPTION_NO);
    }

    @Test
    @DisplayName("驳回：必须填写不少于五字的理由，状态转已驳回且不生成缴费期限")
    void rejectsWithReasonAndNoPaymentDeadline() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO)).thenReturn(Optional.of(pendingAuditAdoption()));
        when(adoptions.updateAudit(eq(ADOPTION_NO), eq(AdoptionStatus.REJECTED), eq(AUDITOR_ID),
                eq("联系不上申请人"), eq(3000), any(LocalDateTime.class))).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        AuditedAdoption result = service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.REJECT, "联系不上申请人", null), AUDITOR_ID);

        assertThat(result.status()).isEqualTo(AdoptionStatus.REJECTED);
        assertThat(result.payDeadline()).isNull();
        ArgumentCaptor<MessageWrite> notice = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(notice.capture());
        assertThat(notice.getValue().title()).contains("未通过").contains("联系不上申请人");
    }

    @Test
    @DisplayName("驳回理由不足五字时返回 30301，且不锁动物、不更新")
    void rejectsBlankReasonBeforeLoadingAdoption() {
        assertThatThrownBy(() -> service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.REJECT, " 不行 ", null), AUDITOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verify(adoptions, never()).findByNoForUpdate(any());
        verify(adoptions, never()).updateAudit(any(), any(), anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("同一单已被他人审核时返回 30407")
    void rejectsAlreadyAuditedAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(adoptionWithStatus(AdoptionStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, null, null), AUDITOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30407));

        verify(adoptions, never()).updateAudit(any(), any(), anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("动物已被他人占据时返回 30407 与原单号")
    void rejectsWhenAnimalBlockedByAnotherAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO)).thenReturn(Optional.of(pendingAuditAdoption()));
        when(animals.lockByCode(ANIMAL_CODE))
                .thenReturn(Optional.of(new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(adoptions.findBlockingAdoptionNo(ANIMAL_ID, ADOPTION_NO))
                .thenReturn(Optional.of("AD20261121009"));

        assertThatThrownBy(() -> service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, null, null), AUDITOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(30407);
                    assertThat(ex.getData()).isEqualTo(java.util.Map.of("activeAdoptionNo", "AD20261121009"));
                });

        verify(adoptions, never()).updateAudit(any(), any(), anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("调整金额但没有说明时返回 30301")
    void rejectsAdjustedAmountWithoutExplanation() {
        assertThatThrownBy(() -> service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, " ", 2800), AUDITOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verify(adoptions, never()).findByNoForUpdate(any());
    }

    @Test
    @DisplayName("认养单不存在时返回 30410")
    void rejectsMissingAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.audit(ADOPTION_NO,
                new AdoptionAuditCommand(AdoptionDecision.APPROVE, null, null), AUDITOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    private AdoptionRecord pendingAuditAdoption() {
        return adoptionWithStatus(AdoptionStatus.PENDING_AUDIT);
    }

    private AdoptionRecord adoptionWithStatus(AdoptionStatus status) {
        return new AdoptionRecord(
                101L,
                ADOPTION_NO,
                1001L,
                ANIMAL_ID,
                ANIMAL_CODE,
                2L,
                3000,
                AdoptionPeriod.MONTH,
                status,
                "13800000000",
                "希望定期看照片",
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }
}
