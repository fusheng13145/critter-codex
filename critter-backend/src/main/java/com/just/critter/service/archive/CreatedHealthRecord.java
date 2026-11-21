package com.just.critter.service.archive;

/**
 * 新增健康记录结果，只回传记录 id，前端据此刷新时间线。
 */
public record CreatedHealthRecord(Long healthRecordId) {
}
