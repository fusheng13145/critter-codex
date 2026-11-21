package com.just.critter.controller.archive;

import com.just.critter.service.archive.GroupMergeCommand;

import java.util.List;

/**
 * 群体合并请求。主编号保留，副编号的目击记录并入主群体。
 */
public record GroupMergeRequest(String mainCode, List<String> sourceCodes, String reason) {

    public GroupMergeCommand toCommand() {
        return new GroupMergeCommand(mainCode, sourceCodes, reason);
    }
}
