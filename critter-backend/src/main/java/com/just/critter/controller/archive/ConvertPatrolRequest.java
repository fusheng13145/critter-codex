package com.just.critter.controller.archive;

import com.just.critter.service.archive.ConvertPatrolCommand;

/**
 * 巡查转目击请求；groupCode 为空表示生成待归属目击。
 */
public record ConvertPatrolRequest(String groupCode) {

    public ConvertPatrolCommand toCommand() {
        return new ConvertPatrolCommand(groupCode);
    }
}
