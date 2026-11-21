package com.just.critter.service.archive;

/**
 * 目击归属命令：groupId 与 groupCode 二选一；pending=true 时清空归属。
 */
public record SightingAssignmentCommand(Long groupId, String groupCode, Boolean pending, String reason) {
}
