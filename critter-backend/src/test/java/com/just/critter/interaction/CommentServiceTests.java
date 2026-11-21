package com.just.critter.interaction;

import com.just.critter.interaction.repository.InteractionRepository;
import com.just.critter.interaction.service.CommentCreateCommand;
import com.just.critter.interaction.service.CommentRecord;
import com.just.critter.interaction.service.CommentReplyCommand;
import com.just.critter.interaction.service.CommentService;
import com.just.critter.interaction.service.CommentStatus;
import com.just.critter.interaction.service.CommentView;
import com.just.critter.interaction.service.CommentWrite;
import com.just.critter.interaction.service.SensitiveWordFilter;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTests {

    private static final long AUTHOR_ID = 1001L;
    private static final long ADMIN_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T06:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private InteractionRepository repository;
    private SensitiveWordFilter filter;
    private AuditLogService audit;
    private CommentService service;

    @BeforeEach
    void setUp() {
        repository = mock(InteractionRepository.class);
        filter = mock(SensitiveWordFilter.class);
        audit = mock(AuditLogService.class);
        service = new CommentService(repository, filter, audit, CLOCK);
    }

    @Test
    @DisplayName("发表留言：普通内容直接可见并写入作者")
    void createsVisibleComment() {
        when(repository.animalExists("CAT-2026-031")).thenReturn(true);
        when(repository.insertComment(any())).thenReturn(7L);
        when(filter.isSensitive("请问这只猫最近还好吗？")).thenReturn(false);

        CommentView view = service.create(new CommentCreateCommand(
                "ANIMAL", "CAT-2026-031", "  请问这只猫最近还好吗？  "), AUTHOR_ID);

        assertThat(view.commentId()).isEqualTo(7L);
        assertThat(view.status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(view.content()).isEqualTo("请问这只猫最近还好吗？");
        ArgumentCaptor<CommentWrite> write = ArgumentCaptor.forClass(CommentWrite.class);
        verify(repository).insertComment(write.capture());
        assertThat(write.getValue().status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(write.getValue().authorId()).isEqualTo(AUTHOR_ID);
    }

    @Test
    @DisplayName("发表留言：命中敏感词进入待审，不直接公开")
    void pendingWhenSensitive() {
        when(repository.animalExists("CAT-2026-031")).thenReturn(true);
        when(repository.insertComment(any())).thenReturn(8L);
        when(filter.isSensitive("加微信详聊")).thenReturn(true);

        CommentView view = service.create(new CommentCreateCommand(
                "ANIMAL", "CAT-2026-031", "加微信详聊"), AUTHOR_ID);

        assertThat(view.status()).isEqualTo(CommentStatus.PENDING_AUDIT);
    }

    @Test
    @DisplayName("发表留言：目标不存在返回 30410")
    void rejectsMissingTarget() {
        when(repository.animalExists("CAT-NONE")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CommentCreateCommand(
                "ANIMAL", "CAT-NONE", "留言内容"), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
        verify(repository, never()).insertComment(any());
    }

    @Test
    @DisplayName("发表留言：业务类型非法返回 30303")
    void rejectsInvalidBizType() {
        assertThatThrownBy(() -> service.create(new CommentCreateCommand(
                "NOTICE", "1", "留言内容"), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));
    }

    @Test
    @DisplayName("公开列表：匿名只请求可见留言，不传作者与管理员标记")
    void listsVisibleForAnonymous() {
        when(repository.countComments(eq("ANIMAL"), eq("CAT-2026-031"), eq(null), eq(false)))
                .thenReturn(1L);
        when(repository.findComments(eq("ANIMAL"), eq("CAT-2026-031"), eq(null), eq(false),
                eq(0L), eq(20)))
                .thenReturn(List.of(record(7L, CommentStatus.VISIBLE, 1001L, false)));

        PageResult<CommentView> page = service.list("ANIMAL", "CAT-2026-031",
                new PageQuery(1, 20), null, false);

        assertThat(page.getList()).extracting(CommentView::commentId).containsExactly(7L);
        assertThat(page.getPage().getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：作者能在公开列表看到自己的待审留言")
    void authorSeesOwnPending() {
        when(repository.countComments(eq("ANIMAL"), eq("CAT-2026-031"), eq(AUTHOR_ID), eq(false)))
                .thenReturn(1L);
        when(repository.findComments(any(), any(), eq(AUTHOR_ID), eq(false), anyLong(), any(Integer.class)))
                .thenReturn(List.of(record(9L, CommentStatus.PENDING_AUDIT, AUTHOR_ID, false)));

        PageResult<CommentView> page = service.list("ANIMAL", "CAT-2026-031",
                new PageQuery(), AUTHOR_ID, false);

        assertThat(page.getList().get(0).status()).isEqualTo(CommentStatus.PENDING_AUDIT);
    }

    @Test
    @DisplayName("回复留言：待审留言回复后转为可见，并写 COMMENT_REPLY 审计")
    void replyPublishesPendingComment() {
        CommentRecord before = record(9L, CommentStatus.PENDING_AUDIT, AUTHOR_ID, false);
        when(repository.findComment(9L)).thenReturn(Optional.of(before));
        when(repository.replyComment(eq(9L), eq("已核实，请放心。"), eq(true),
                eq(CommentStatus.VISIBLE), any())).thenReturn(1);

        CommentView view = service.reply(9L, new CommentReplyCommand("已核实，请放心。", true));

        assertThat(view.replyContent()).isEqualTo("已核实，请放心。");
        assertThat(view.replyVisible()).isTrue();
        assertThat(view.status()).isEqualTo(CommentStatus.VISIBLE);
        verify(repository).replyComment(eq(9L), eq("已核实，请放心。"), eq(true),
                eq(CommentStatus.VISIBLE), any());
        verify(audit).success(eq("COMMENT_REPLY"), eq("COMMENT"), eq("9"), eq(before), any());
    }

    @Test
    @DisplayName("回复留言：记录不存在返回 30410")
    void replyMissingReturns30410() {
        when(repository.findComment(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reply(9L, new CommentReplyCommand("回复", true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("回复留言：缺少可见性返回 30301")
    void replyRequiresVisibleFlag() {
        assertThatThrownBy(() -> service.reply(9L, new CommentReplyCommand("回复", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
    }

    private CommentRecord record(long id, CommentStatus status, long authorId, boolean replyVisible) {
        return new CommentRecord(id, "ANIMAL", "CAT-2026-031", authorId, "小林",
                "请问这只猫最近还好吗？", status, "已核实，请放心。", replyVisible,
                LocalDateTime.of(2026, 11, 21, 12, 0));
    }
}
