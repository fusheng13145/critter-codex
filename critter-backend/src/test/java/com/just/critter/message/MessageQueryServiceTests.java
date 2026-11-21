package com.just.critter.message;

import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageQueryService;
import com.just.critter.message.service.MessageRecord;
import com.just.critter.message.service.MessageTypeCount;
import com.just.critter.message.service.MessageUnreadCount;
import com.just.critter.message.service.MessageView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageQueryServiceTests {

    private static final long USER_ID = 1001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 11, 21, 12, 0).minusDays(180);

    private MessageRepository messages;
    private MessageQueryService service;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        service = new MessageQueryService(messages, CLOCK);
    }

    @Test
    @DisplayName("消息列表：按已读标记、类型与分页查询，并把记录映射为视图")
    void listsMessagesWithFilters() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 21, 10, 5);
        when(messages.count(USER_ID, false, "APPROVAL_RESULT", SINCE)).thenReturn(21L);
        when(messages.findPage(USER_ID, false, "APPROVAL_RESULT", SINCE, 10, 10)).thenReturn(List.of(
                new MessageRecord(1L, "APPROVAL_RESULT", "认养申请已通过",
                        "ADOPTION", "AD20261121003", false,
                        null, createdAt)));

        PageResult<MessageView> result = service.list(USER_ID, false,
                "APPROVAL_RESULT", new PageQuery(2, 10));

        assertThat(result.getList()).containsExactly(new MessageView(1L, "APPROVAL_RESULT",
                "认养申请已通过", "ADOPTION", "AD20261121003", false,
                null, createdAt));
        assertThat(result.getPage().getPage()).isEqualTo(2);
        assertThat(result.getPage().getTotal()).isEqualTo(21);
        verify(messages).findPage(USER_ID, false, "APPROVAL_RESULT", SINCE, 10, 10);
    }

    @Test
    @DisplayName("消息类型非法时返回 30303，不访问仓储")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> service.list(USER_ID, null, "UNKNOWN", new PageQuery(1, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));

        verifyNoInteractions(messages);
    }

    @Test
    @DisplayName("四类未读数：缺少的类型补零，只统计 180 天内未读消息")
    void countsFourUnreadGroups() {
        LocalDateTime since = LocalDateTime.of(2026, 11, 21, 12, 0).minusDays(180);
        when(messages.countUnreadByType(eq(USER_ID), eq(since))).thenReturn(List.of(
                new MessageTypeCount("APPROVAL_RESULT", 3L),
                new MessageTypeCount("ORDER_PROGRESS", 2L)));

        MessageUnreadCount counts = service.unread(USER_ID);

        assertThat(counts.approvalResult()).isEqualTo(3L);
        assertThat(counts.orderProgress()).isEqualTo(2L);
        assertThat(counts.donationProgress()).isZero();
        assertThat(counts.adoptionExpiry()).isZero();
        verify(messages).countUnreadByType(USER_ID, since);
    }

    @Test
    @DisplayName("无消息时返回空分页，不查询列表")
    void returnsEmptyPageWhenTotalIsZero() {
        when(messages.count(USER_ID, null, null, SINCE)).thenReturn(0L);

        PageResult<MessageView> result = service.list(USER_ID, null, null, new PageQuery(1, 20));

        assertThat(result.getList()).isEmpty();
        assertThat(result.getPage().getTotal()).isZero();
        verify(messages, never()).findPage(eq(USER_ID), any(), any(), eq(SINCE), anyLong(), anyInt());
    }
}
