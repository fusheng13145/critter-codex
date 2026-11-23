package com.just.critter.service.archive;

/**
 * 巡查转目击命令；groupCode 为空时生成待归属目击。
 */
public record ConvertPatrolCommand(String groupCode) {
}
