package com.just.critter.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 操作日志检索出入参（P10 日志页）。 */
public final class LogDtos {

    private LogDtos() {
    }

    public record LogItem(Long id, long actorId, String actorRole, String action,
                          String bizType, String bizId, String beforeJson, String afterJson,
                          String ip, String result, Integer failCode, String requestId,
                          LocalDateTime createdAt) {
    }

    public record LogPage(List<LogItem> list, PageMeta page) {
    }

    public record PageMeta(int page, int size, long total, long pages) {
    }
}
