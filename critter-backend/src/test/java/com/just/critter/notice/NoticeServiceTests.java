package com.just.critter.notice;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.notice.repository.NoticeRepository;
import com.just.critter.notice.service.NoticeCommand;
import com.just.critter.notice.service.NoticeDetail;
import com.just.critter.notice.service.NoticeService;
import com.just.critter.notice.service.NoticeSummaryRecord;
import com.just.critter.notice.service.NoticeView;
import com.just.critter.notice.service.NoticeWrite;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoticeServiceTests {

    private static final long AUTHOR_ID = 9001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 12, 0);

    private NoticeRepository notices;
    private SequencePort sequences;
    private NoticeService service;

    @BeforeEach
    void setUp() {
        notices = mock(NoticeRepository.class);
        sequences = mock(SequencePort.class);
        service = new NoticeService(notices, sequences, CLOCK);
    }

    @Test
    @DisplayName("公告列表：按分类分页，返回置顶优先的摘要")
    void listsNoticesWithCategoryAndPaging() {
        when(notices.count("PROGRESS")).thenReturn(23L);
        when(notices.findPage("PROGRESS", 20, 10)).thenReturn(List.of(
                new NoticeSummaryRecord(7L, "NT20261121001", "PROGRESS",
                        "TNR 进展", true, NOW.minusDays(1)),
                new NoticeSummaryRecord(8L, "NT20261121002", "PROGRESS",
                        "本周救助小结", false, NOW.minusDays(2))));

        PageResult<NoticeView> result = service.list("progress", new PageQuery(3, 10));

        assertThat(result.getList()).containsExactly(
                new NoticeView(7L, "NT20261121001", "PROGRESS", "TNR 进展", true, NOW.minusDays(1)),
                new NoticeView(8L, "NT20261121002", "PROGRESS", "本周救助小结", false, NOW.minusDays(2)));
        assertThat(result.getPage().getTotal()).isEqualTo(23);
        assertThat(result.getPage().getPages()).isEqualTo(3);
        verify(notices).findPage("PROGRESS", 20, 10);
    }

    @Test
    @DisplayName("公告分类非法返回 30303，不访问仓储")
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> service.list("UNKNOWN", new PageQuery(1, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));

        verifyNoInteractions(notices);
    }

    @Test
    @DisplayName("总数 0 时返回空分页，不查列表")
    void returnsEmptyPageWhenNoNotices() {
        when(notices.count(null)).thenReturn(0L);

        PageResult<NoticeView> result = service.list(null, new PageQuery(1, 20));

        assertThat(result.getList()).isEmpty();
        assertThat(result.getPage().getTotal()).isZero();
        verify(notices, never()).findPage(any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("公告详情返回正文与发布人")
    void returnsNoticeDetail() {
        NoticeDetail detail = new NoticeDetail(
                7L, "NT20261121001", "PROGRESS", "TNR 进展",
                "本周完成三只猫的抓捕与送医。", AUTHOR_ID, "纪老师", true, NOW, NOW);
        when(notices.findById(7L)).thenReturn(Optional.of(detail));

        NoticeDetail result = service.detail(7L);

        assertThat(result.noticeNo()).isEqualTo("NT20261121001");
        assertThat(result.content()).contains("三只猫");
        assertThat(result.authorName()).isEqualTo("纪老师");
    }

    @Test
    @DisplayName("公告编号非法或不存在返回 30301 / 30410")
    void rejectsInvalidOrMissingDetail() {
        assertThatThrownBy(() -> service.detail(0L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        when(notices.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("发布公告：生成 NT 编号，裁剪标题正文并立即发布")
    void publishesNotice() {
        when(sequences.next("NOTICE", 2026)).thenReturn(1);
        when(notices.insert(any())).thenReturn(8L);

        NoticeView result = service.publish(new NoticeCommand(
                "progress", "  TNR 进展  ", "  本周完成三只猫的抓捕与送医。  ", true), AUTHOR_ID);

        assertThat(result.noticeId()).isEqualTo(8L);
        assertThat(result.noticeNo()).isEqualTo("NT20261121001");
        assertThat(result.category()).isEqualTo("PROGRESS");
        assertThat(result.title()).isEqualTo("TNR 进展");
        assertThat(result.pinned()).isTrue();
        assertThat(result.publishedAt()).isEqualTo(NOW);

        ArgumentCaptor<NoticeWrite> write = ArgumentCaptor.forClass(NoticeWrite.class);
        verify(notices).insert(write.capture());
        assertThat(write.getValue().noticeNo()).isEqualTo("NT20261121001");
        assertThat(write.getValue().content()).isEqualTo("本周完成三只猫的抓捕与送医。");
        assertThat(write.getValue().authorId()).isEqualTo(AUTHOR_ID);
        assertThat(write.getValue().pinned()).isTrue();
        assertThat(write.getValue().publishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("发布公告：分类非法、标题或正文为空、内容超长返回 30301 / 30303")
    void rejectsInvalidPublishCommand() {
        assertThatThrownBy(() -> service.publish(new NoticeCommand(
                "UNKNOWN", "标题", "正文", false), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30303));

        assertThatThrownBy(() -> service.publish(new NoticeCommand(
                "PROGRESS", "  ", "正文", false), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        assertThatThrownBy(() -> service.publish(new NoticeCommand(
                "PROGRESS", "标题", "x".repeat(20001), false), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(notices, sequences);
    }

    @Test
    @DisplayName("首页 highlights：条数收敛到 1..10，置顶与公示优先由仓储排序")
    void highlightsClampLimit() {
        when(notices.findHighlights(5)).thenReturn(List.of(
                new NoticeSummaryRecord(1L, "NT20261121001", "PUBLICITY",
                        "结项公示", true, NOW),
                new NoticeSummaryRecord(2L, "NT20261121002", "PUBLICITY",
                        "认养公示", false, NOW.minusHours(1))));

        var list = service.highlights(99);

        assertThat(list).hasSize(2);
        assertThat(list.getFirst().pinned()).isTrue();
        verify(notices).findHighlights(5);
    }

    @Test
    @DisplayName("写入 0 行返回 50002")
    void rejectsFailedInsert() {
        when(sequences.next("NOTICE", 2026)).thenReturn(2);
        when(notices.insert(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.publish(new NoticeCommand(
                "SCIENCE", "校园鸟类观察", "本周记录到三种候鸟。", false), AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(50002));
    }
}
