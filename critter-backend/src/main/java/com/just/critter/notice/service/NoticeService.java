package com.just.critter.notice.service;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.notice.repository.NoticeRepository;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NoticeService {

    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 20000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String SEQUENCE_TYPE = "NOTICE";

    private final NoticeRepository notices;
    private final SequencePort sequences;
    private final Clock clock;

    public NoticeService(NoticeRepository notices, SequencePort sequences, Clock clock) {
        this.notices = notices;
        this.sequences = sequences;
        this.clock = clock;
    }

    public PageResult<NoticeView> list(String category, PageQuery query) {
        PageQuery safe = query == null ? new PageQuery() : query.normalized();
        String normalizedCategory = normalizeCategory(category);

        long total = notices.count(normalizedCategory);
        if (total == 0) {
            return PageResult.of(List.of(), safe.getPage(), safe.getSize(), 0);
        }
        List<NoticeView> list = notices.findPage(normalizedCategory, safe.offset(), safe.getSize())
                .stream()
                .map(this::toView)
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), total);
    }

    public NoticeDetail detail(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "id"));
        }
        return notices.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public NoticeView publish(NoticeCommand command, long authorId) {
        if (command == null) {
            throw invalid("body");
        }
        String category = normalizeCategory(command.category());
        String title = requireText(command.title(), TITLE_MAX, "title");
        String content = requireText(command.content(), CONTENT_MAX, "content");
        boolean pinned = Boolean.TRUE.equals(command.pinned());
        LocalDateTime publishedAt = LocalDateTime.now(clock);
        String noticeNo = nextNoticeNo(publishedAt);

        long id = notices.insert(new NoticeWrite(
                noticeNo, category, title, content, authorId, pinned, publishedAt));
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return new NoticeView(id, noticeNo, category, title, pinned, publishedAt);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return NoticeCategory.parse(category).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "category"));
        }
    }

    private String requireText(String value, int maxLength, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw invalid(field);
        }
        return text;
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
    }

    private String nextNoticeNo(LocalDateTime publishedAt) {
        int serial = sequences.next(SEQUENCE_TYPE, publishedAt.getYear());
        if (serial <= 0 || serial > 999) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return "NT" + publishedAt.format(DAY) + String.format("%03d", serial);
    }

    private NoticeView toView(NoticeSummaryRecord record) {
        return new NoticeView(record.id(), record.noticeNo(), record.category(),
                record.title(), record.pinned(), record.publishedAt());
    }
}
