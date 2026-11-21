package com.just.critter.service.archive;

import java.util.List;

/**
 * 档案合并命令：主编号保留，副编号作为可检索别名继续存在。
 */
public record AnimalMergeCommand(String mainCode, List<String> sourceCodes, String reason) {
}
