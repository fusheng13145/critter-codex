package com.just.critter.notice.service;

/**
 * 发布公告的命令，字段来自管理员提交的表单。
 */
public record NoticeCommand(String category, String title, String content, Boolean pinned) {
}
