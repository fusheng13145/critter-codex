package com.just.critter.notice.service;

import java.time.LocalDateTime;

/**
 * 公告详情，包含正文与发布人昵称。
 */
public record NoticeDetail(long noticeId,
                           String noticeNo,
                           String category,
                           String title,
                           String content,
                           Long authorId,
                           String authorName,
                           boolean pinned,
                           LocalDateTime publishedAt,
                           LocalDateTime createdAt) {
}
