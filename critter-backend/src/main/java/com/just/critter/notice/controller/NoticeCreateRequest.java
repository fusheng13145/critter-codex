package com.just.critter.notice.controller;

/**
 * 发布公告的请求体，必填与长度校验在 NoticeService 统一完成。
 */
public record NoticeCreateRequest(String category, String title, String content, Boolean pinned) {
}
