package com.just.critter.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 附件出入参：POST /files 只为拿 fileId，业务表只存 id（两步上传）。 */
public final class FileDtos {

    private FileDtos() {
    }

    public record UploadResponse(String fileId, String url, String contentUrl, String name,
                                 long size, String mime, String auditStatus) {
    }

    public record FileMeta(String fileId, String bizType, String bizId, String name, String mime,
                           long size, String auditStatus, String url, String contentUrl,
                           LocalDateTime createdAt) {
    }

    /**
     * 附件审核工作台的分页列表（GET /files，管理员）。
     * 属辅助端点：契约清单里只有审核动作，但审核总得先能看到待审列表才有入口。
     */
    public record FilePage(List<FileMeta> list, PageMeta page) {
    }

    public record PageMeta(int page, int size, long total, long pages) {
    }
}
