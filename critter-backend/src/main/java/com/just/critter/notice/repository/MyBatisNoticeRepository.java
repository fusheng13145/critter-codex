package com.just.critter.notice.repository;

import com.just.critter.domain.model.NoticeEntity;
import com.just.critter.domain.model.NoticeRow;
import com.just.critter.mapper.NoticeMapper;
import com.just.critter.notice.service.NoticeDetail;
import com.just.critter.notice.service.NoticeSummaryRecord;
import com.just.critter.notice.service.NoticeWrite;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisNoticeRepository implements NoticeRepository {

    private final NoticeMapper mapper;

    public MyBatisNoticeRepository(NoticeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long count(String category) {
        return mapper.count(category);
    }

    @Override
    public List<NoticeSummaryRecord> findPage(String category, long offset, int size) {
        return mapper.findPage(category, offset, size).stream()
                .map(row -> new NoticeSummaryRecord(
                        row.getId() == null ? 0 : row.getId(),
                        row.getNoticeNo(),
                        row.getCategory(),
                        row.getTitle(),
                        Boolean.TRUE.equals(row.getPinned()),
                        row.getPublishedAt()))
                .toList();
    }

    @Override
    public Optional<NoticeDetail> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toDetail);
    }

    @Override
    public long insert(NoticeWrite notice) {
        NoticeEntity entity = new NoticeEntity();
        entity.setNoticeNo(notice.noticeNo());
        entity.setCategory(notice.category());
        entity.setTitle(notice.title());
        entity.setContent(notice.content());
        entity.setAuthorId(notice.authorId());
        entity.setPinned(notice.pinned());
        entity.setPublishedAt(notice.publishedAt());
        entity.setCreatedAt(notice.publishedAt());
        entity.setUpdatedAt(notice.publishedAt());
        entity.setCreatedBy(notice.authorId());
        mapper.insert(entity);
        return entity.getId() == null ? 0 : entity.getId();
    }

    private NoticeDetail toDetail(NoticeRow row) {
        return new NoticeDetail(
                row.getId() == null ? 0 : row.getId(),
                row.getNoticeNo(),
                row.getCategory(),
                row.getTitle(),
                row.getContent(),
                row.getAuthorId(),
                row.getAuthorName(),
                Boolean.TRUE.equals(row.getPinned()),
                row.getPublishedAt(),
                row.getCreatedAt());
    }
}
