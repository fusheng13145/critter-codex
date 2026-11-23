package com.just.critter.notice.repository;

import com.just.critter.notice.service.NoticeDetail;
import com.just.critter.notice.service.NoticeSummaryRecord;
import com.just.critter.notice.service.NoticeWrite;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository {

    long count(String category);

    List<NoticeSummaryRecord> findPage(String category, long offset, int size);

    List<NoticeSummaryRecord> findHighlights(int limit);

    Optional<NoticeDetail> findById(long id);

    long insert(NoticeWrite notice);
}
