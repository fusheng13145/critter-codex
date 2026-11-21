package com.just.critter.message;

import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageReadFailure;
import com.just.critter.message.service.MessageReadResult;
import com.just.critter.message.service.MessageReadService;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageReadServiceTests {

    private static final long USER_ID = 1001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private MessageRepository messages;
    private MessageReadService service;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        service = new MessageReadService(messages, CLOCK);
    }

    @Test
    @DisplayName("批量已读：本人未读消息置为已读并写入读取时间")
    void marksUnreadOwnedMessage() {
        when(messages.findReadFlag(1L, USER_ID)).thenReturn(Optional.of(false));
        when(messages.markRead(1L, USER_ID, LocalDateTime.of(2026, 11, 21, 12, 0))).thenReturn(1);

        MessageReadResult result = service.markRead(USER_ID, List.of(1L));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        verify(messages).markRead(1L, USER_ID, LocalDateTime.of(2026, 11, 21, 12, 0));
    }

    @Test
    @DisplayName("已读消息重复标记仍计成功，不重复写库")
    void treatsAlreadyReadMessageAsSuccess() {
        when(messages.findReadFlag(1L, USER_ID)).thenReturn(Optional.of(true));

        MessageReadResult result = service.markRead(USER_ID, List.of(1L));

        assertThat(result.success()).isEqualTo(1);
        verify(messages, never()).markRead(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("消息不存在或不属于当前用户时返回逐条 30410")
    void reportsMissingMessageAsFailure() {
        when(messages.findReadFlag(9L, USER_ID)).thenReturn(Optional.empty());

        MessageReadResult result = service.markRead(USER_ID, List.of(9L));

        assertThat(result.success()).isZero();
        assertThat(result.failed()).containsExactly(new MessageReadFailure("9", 30410, "记录不存在"));
        verify(messages, never()).markRead(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("重复编号只处理一次")
    void deduplicatesIds() {
        when(messages.findReadFlag(1L, USER_ID)).thenReturn(Optional.of(false));
        when(messages.markRead(anyLong(), anyLong(), any())).thenReturn(1);

        MessageReadResult result = service.markRead(USER_ID, List.of(1L, 1L));

        assertThat(result.success()).isEqualTo(1);
        verify(messages).findReadFlag(1L, USER_ID);
        verify(messages).markRead(1L, USER_ID, LocalDateTime.of(2026, 11, 21, 12, 0));
    }

    @Test
    @DisplayName("编号为空时返回 30301，不访问仓储")
    void rejectsEmptyIds() {
        assertThatThrownBy(() -> service.markRead(USER_ID, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(messages);
    }
}
