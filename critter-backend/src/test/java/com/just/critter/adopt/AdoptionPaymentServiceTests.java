package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptableAnimal;
import com.just.critter.adopt.domain.AdoptionPaymentCommand;
import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionRecord;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptableAnimalRepository;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.adopt.service.AdoptionActivation;
import com.just.critter.adopt.service.AdoptionPaymentResult;
import com.just.critter.adopt.service.AdoptionPaymentService;
import com.just.critter.adopt.service.AdoptionPaymentWrite;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.domain.enums.AnimalStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionPaymentServiceTests {

    private static final String ADOPTION_NO = "AD20261121003";
    private static final long ADOPTION_ID = 101L;
    private static final long ANIMAL_ID = 11L;
    private static final String ANIMAL_CODE = "CAT-2026-031";
    private static final long VERIFIER_ID = 9001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private AdoptionRepository adoptions;
    private AdoptableAnimalRepository animals;
    private SequencePort sequences;
    private MessageRepository messages;
    private AdoptionPaymentService service;

    @BeforeEach
    void setUp() {
        adoptions = mock(AdoptionRepository.class);
        animals = mock(AdoptableAnimalRepository.class);
        sequences = mock(SequencePort.class);
        messages = mock(MessageRepository.class);
        service = new AdoptionPaymentService(adoptions, animals, sequences, messages, CLOCK);
    }

    @Test
    @DisplayName("缴费入账：写缴费、转 ACTIVE、算起止日期、发证书号并标记动物已认养")
    void paysAndActivatesAdoption() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT, 3000)));
        when(adoptions.hasPayment(ADOPTION_ID)).thenReturn(false);
        when(animals.lockByCode(ANIMAL_CODE)).thenReturn(Optional.of(
                new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(sequences.next("ADOPTION_PAYMENT", 2026)).thenReturn(1);
        when(sequences.next("ADOPTION_CERTIFICATE", 2026)).thenReturn(31);
        when(adoptions.insertPayment(any())).thenReturn(1);
        when(adoptions.activate(any())).thenReturn(1);
        when(animals.markAdopted(ANIMAL_ID)).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        AdoptionPaymentResult result = service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(3000,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121001"), "线下转账"), VERIFIER_ID);

        assertThat(result.status()).isEqualTo(AdoptionStatus.ACTIVE);
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(result.certificateNo()).isEqualTo("QYWL-AD-2026-0031");
        assertThat(result.animalStatus()).isEqualTo("ADOPTED");

        ArgumentCaptor<AdoptionPaymentWrite> payment = ArgumentCaptor.forClass(AdoptionPaymentWrite.class);
        verify(adoptions).insertPayment(payment.capture());
        assertThat(payment.getValue().paymentNo()).isEqualTo("PAY20261121001");
        assertThat(payment.getValue().amount()).isEqualTo(3000);
        assertThat(payment.getValue().verifierId()).isEqualTo(VERIFIER_ID);
        assertThat(payment.getValue().voucherFileIds()).containsExactly("F20261121001");

        ArgumentCaptor<AdoptionActivation> activation = ArgumentCaptor.forClass(AdoptionActivation.class);
        verify(adoptions).activate(activation.capture());
        assertThat(activation.getValue().startDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(activation.getValue().endDate()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(activation.getValue().certificateNo()).isEqualTo("QYWL-AD-2026-0031");
        verify(animals).markAdopted(ANIMAL_ID);
        ArgumentCaptor<MessageWrite> notice = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(notice.capture());
        assertThat(notice.getValue().receiverId()).isEqualTo(1001L);
        assertThat(notice.getValue().type()).isEqualTo(MessageType.APPROVAL_RESULT.name());
        assertThat(notice.getValue().title()).contains("入账");
        assertThat(notice.getValue().bizType()).isEqualTo("ADOPTION");
        assertThat(notice.getValue().bizId()).isEqualTo(ADOPTION_NO);
    }

    @Test
    @DisplayName("季度方案入账后结束日期为三个月后")
    void quarterPlanAddsThreeMonths() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT, 8000, AdoptionPeriod.QUARTER)));
        when(animals.lockByCode(ANIMAL_CODE)).thenReturn(Optional.of(
                new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.RELEASED)));
        when(sequences.next("ADOPTION_PAYMENT", 2026)).thenReturn(2);
        when(sequences.next("ADOPTION_CERTIFICATE", 2026)).thenReturn(32);
        when(adoptions.insertPayment(any())).thenReturn(1);
        when(adoptions.activate(any())).thenReturn(1);
        when(animals.markAdopted(ANIMAL_ID)).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        AdoptionPaymentResult result = service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(8000,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121002"), null), VERIFIER_ID);

        assertThat(result.endDate()).isEqualTo(LocalDate.of(2027, 2, 21));
    }

    @Test
    @DisplayName("金额不符且没有差额说明时返回 30408，不写任何数据")
    void rejectsMismatchedAmountWithoutNote() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT, 3000)));
        when(adoptions.hasPayment(ADOPTION_ID)).thenReturn(false);
        when(animals.lockByCode(ANIMAL_CODE)).thenReturn(Optional.of(
                new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));

        assertThatThrownBy(() -> service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(2800,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121001"), " "), VERIFIER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30408));

        verify(adoptions, never()).insertPayment(any());
        verify(adoptions, never()).activate(any());
        verify(animals, never()).markAdopted(anyLong());
    }

    @Test
    @DisplayName("金额不符但填写不少于五字的差额说明时允许强制入账并留存实收金额")
    void allowsMismatchedAmountWithNote() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT, 3000)));
        when(adoptions.hasPayment(ADOPTION_ID)).thenReturn(false);
        when(animals.lockByCode(ANIMAL_CODE)).thenReturn(Optional.of(
                new AdoptableAnimal(ANIMAL_ID, ANIMAL_CODE, AnimalStatus.ON_CAMPUS)));
        when(sequences.next("ADOPTION_PAYMENT", 2026)).thenReturn(3);
        when(sequences.next("ADOPTION_CERTIFICATE", 2026)).thenReturn(33);
        when(adoptions.insertPayment(any())).thenReturn(1);
        when(adoptions.activate(any())).thenReturn(1);
        when(animals.markAdopted(ANIMAL_ID)).thenReturn(1);
        when(messages.insert(any())).thenReturn(1);

        service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(2800,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121003"), "手续费差额已说明"), VERIFIER_ID);

        ArgumentCaptor<AdoptionPaymentWrite> payment = ArgumentCaptor.forClass(AdoptionPaymentWrite.class);
        verify(adoptions).insertPayment(payment.capture());
        assertThat(payment.getValue().amount()).isEqualTo(2800);
        assertThat(payment.getValue().note()).isEqualTo("手续费差额已说明");
    }

    @Test
    @DisplayName("非待缴费状态重复入账返回 30402")
    void rejectsNonPendingPaymentStatus() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.ACTIVE, 3000)));

        assertThatThrownBy(() -> service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(3000,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121001"), null), VERIFIER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));
        verify(adoptions, never()).insertPayment(any());
    }

    @Test
    @DisplayName("已存在缴费流水时拒绝再次入账")
    void rejectsDuplicatePayment() {
        when(adoptions.findByNoForUpdate(ADOPTION_NO))
                .thenReturn(Optional.of(record(AdoptionStatus.PENDING_PAYMENT, 3000)));
        when(adoptions.hasPayment(ADOPTION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(3000,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of("F20261121001"), null), VERIFIER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));
    }

    @Test
    @DisplayName("缺少凭证返回 30301")
    void rejectsMissingVoucher() {
        assertThatThrownBy(() -> service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(3000,
                        OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                        List.of(), null), VERIFIER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        verify(adoptions, never()).findByNoForUpdate(any());
    }

    @Test
    @DisplayName("到账时间晚于当前时间返回 30301")
    void rejectsFuturePaidAt() {
        assertThatThrownBy(() -> service.pay(ADOPTION_NO,
                new AdoptionPaymentCommand(3000,
                        OffsetDateTime.parse("2026-11-22T10:00:00+08:00"),
                        List.of("F20261121001"), null), VERIFIER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    private AdoptionRecord record(AdoptionStatus status, int amount) {
        return record(status, amount, AdoptionPeriod.MONTH);
    }

    private AdoptionRecord record(AdoptionStatus status, int amount, AdoptionPeriod period) {
        return new AdoptionRecord(
                ADOPTION_ID,
                ADOPTION_NO,
                1001L,
                ANIMAL_ID,
                ANIMAL_CODE,
                2L,
                amount,
                period,
                status,
                "13800000000",
                "希望定期看照片",
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }
}
