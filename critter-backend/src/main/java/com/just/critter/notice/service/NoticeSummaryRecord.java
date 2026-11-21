package com.just.critter.notice.service;

import java.time.LocalDateTime;

/**
 * 仓储层公告列表行。
 */
public record NoticeSummaryRecord(long id,
                                 String noticeNo,
                                 String category,
                                 String title,
                                 boolean pinned,
                                 LocalDateTime publishedAt) {
}
