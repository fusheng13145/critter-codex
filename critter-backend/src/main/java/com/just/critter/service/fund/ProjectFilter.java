package com.just.critter.service.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;

import java.util.List;

/**
 * 项目列表筛选：status 可多选；公开接口会自动与可见状态取交集。
 */
public record ProjectFilter(List<ProjectStatus> statuses, ProjectTargetType targetType, String keyword) {
}
