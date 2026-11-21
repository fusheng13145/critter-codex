package com.just.critter.controller.archive;

import com.just.critter.service.archive.SightingAssignmentCommand;

/**
 * 目击归属请求。groupId/groupCode 指定群体；pending=true 表示重新标记为待归属。
 */
public record UpdateSightingRequest(Long groupId, String groupCode, Boolean pending, String reason) {

    public SightingAssignmentCommand toCommand() {
        return new SightingAssignmentCommand(groupId, groupCode, pending, reason);
    }
}
