package com.just.critter.service.archive;

import java.util.List;

/**
 * 群体合并命令。
 */
public record GroupMergeCommand(String mainCode, List<String> sourceCodes, String reason) {
}
