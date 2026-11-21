package com.just.critter.notice.service;

/**
 * 公告列表项，只含摘要字段；详情正文由 NoticeDetail 返回。
 */
public record NoticeView(long noticeId,
                         String noticeNo,
                         String category,
                         String title,
                         boolean pinned,
                         java.time.LocalDateTime publishedAt) {
}
