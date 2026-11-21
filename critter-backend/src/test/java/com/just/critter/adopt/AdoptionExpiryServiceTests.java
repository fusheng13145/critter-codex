package com.just.critter.adopt;

import com.just.critter.adopt.repository.AdoptionExpiryRepository;
import com.just.critter.adopt.service.AdoptionExpiryResult;
import com.just.critter.adopt.service.AdoptionExpiryService;
import com.just.critter.adopt.service.ExpiringAdoption;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BR-05：到期前 7 日提醒一次；过期转 EXPIRED 并释放动物；任务可重跑。
 */
class AdoptionExpiryServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:00:00Z"), ZONE);

    private AdoptionExpiryRepository expiry;
    private MessageRepository messages;
    private AuditLogService auditLog;
    private AdoptionExpiryService service;

    @BeforeEach
    void setUp() {
        expiry = mock(AdoptionExpiryRepository.class);
        messages = mock(MessageRepository.class);
        auditLog = mock(AuditLogService.class);
        service = new AdoptionExpiryService(expiry, messages, auditLog, CLOCK);
        lenient().when(messages.insert(any(MessageWrite.class))).thenReturn(1);
    }

    @Test
    @DisplayName("到期前七日：写 ADOPTION_EXPIRY，接收人为认养人")
    void remindsAdoptionEndingInSevenDays() {
        ExpiringAdoption adoption = adoption("AD20261121001", LocalDate.parse("2026-11-28"));
        when(expiry.findExpiring(LocalDate.parse("2026-11-28"))).thenReturn(List.of(adoption));
        when(messages.exists("ADOPTION_EXPIRY", "AD20261121001", 9001L)).thenReturn(false);

        AdoptionExpiryResult result = service.process(LocalDate.parse("2026-11-21"));

        assertThat(result.reminded()).isEqualTo(1);
        ArgumentCaptor<MessageWrite> captor = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(captor.capture());
        MessageWrite message = captor.getValue();
        assertThat(message.receiverId()).isEqualTo(9001L);
        assertThat(message.type()).isEqualTo("ADOPTION_EXPIRY");
        assertThat(message.bizType()).isEqualTo("ADOPTION");
        assertThat(message.bizId()).isEqualTo("AD20261121001");
        assertThat(message.title()).contains("2026-11-28");
        assertThat(message.title()).contains("CAT-2026-031");
    }

    @Test
    @DisplayName("同一天重跑：已存在提醒则不重复写")
    void skipsAlreadyReminded() {
        ExpiringAdoption adoption = adoption("AD20261121001", LocalDate.parse("2026-11-28"));
        when(expiry.findExpiring(LocalDate.parse("2026-11-28"))).thenReturn(List.of(adoption));
        when(messages.exists("ADOPTION_EXPIRY", "AD20261121001", 9001L)).thenReturn(true);

        AdoptionExpiryResult result = service.process(LocalDate.parse("2026-11-21"));

        assertThat(result.reminded()).isZero();
        verify(messages, never()).insert(any(MessageWrite.class));
    }

    @Test
    @DisplayName("已过期：转 EXPIRED 并释放动物")
    void expiresOverdueAndReleasesAnimal() {
        ExpiringAdoption adoption = adoption("AD20261101001", LocalDate.parse("2026-11-20"));
        when(expiry.findExpired(LocalDate.parse("2026-11-21"))).thenReturn(List.of(adoption));
        when(expiry.expire(21L, LocalDateTime.parse("2026-11-21T10:00:00"))).thenReturn(1);
        when(expiry.releaseAnimal(11L, LocalDateTime.parse("2026-11-21T10:00:00"))).thenReturn(1);

        AdoptionExpiryResult result = service.process(LocalDate.parse("2026-11-21"));

        assertThat(result.expired()).isEqualTo(1);
        verify(expiry).expire(21L, LocalDateTime.parse("2026-11-21T10:00:00"));
        verify(expiry).releaseAnimal(11L, LocalDateTime.parse("2026-11-21T10:00:00"));
    }

    @Test
    @DisplayName("无待提醒与待过期记录：不查消息表")
    void doesNothingWithoutCandidates() {
        when(expiry.findExpiring(any())).thenReturn(List.of());
        when(expiry.findExpired(any())).thenReturn(List.of());

        AdoptionExpiryResult result = service.process(LocalDate.parse("2026-11-21"));

        assertThat(result.reminded()).isZero();
        assertThat(result.expired()).isZero();
        verifyNoInteractions(messages);
    }

    @Test
    @DisplayName("提醒写入 0 行：50002，整笔任务回滚")
    void failsWhenMessageInsertReturnsZero() {
        ExpiringAdoption adoption = adoption("AD20261121001", LocalDate.parse("2026-11-28"));
        when(expiry.findExpiring(LocalDate.parse("2026-11-28"))).thenReturn(List.of(adoption));
        when(messages.exists(anyString(), anyString(), anyLong())).thenReturn(false);
        when(messages.insert(any(MessageWrite.class))).thenReturn(0);

        assertThatThrownBy(() -> service.process(LocalDate.parse("2026-11-21")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(50002));
    }

    private ExpiringAdoption adoption(String adoptionNo, LocalDate endDate) {
        return new ExpiringAdoption(21L, adoptionNo, 9001L, 11L, "CAT-2026-031", endDate);
    }
}
