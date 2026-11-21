package com.just.critter.controller.dto;

import java.time.LocalDateTime;

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
}
