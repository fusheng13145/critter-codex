package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;

import java.time.OffsetDateTime;

/**
 * 创建项目的结果。默认 DRAFT；命令带 submit=true 时直接返回 PENDING_AUDIT。
 */
public record CreatedProject(String projectNo, ProjectStatus status, OffsetDateTime createdAt) {
}
