package com.just.critter.notice.service;

import java.time.LocalDateTime;

/**
 * 新增公告的写入对象。
 */
public record NoticeWrite(String noticeNo,
                          String category,
                          String title,
                          String content,
                          long authorId,
                          boolean pinned,
                          LocalDateTime publishedAt) {
}
