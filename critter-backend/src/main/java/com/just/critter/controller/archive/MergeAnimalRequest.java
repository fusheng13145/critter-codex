package com.just.critter.controller.archive;

import com.just.critter.service.archive.AnimalMergeCommand;

import java.util.List;

/**
 * 档案合并请求体。sourceCodes 可一次传多个副编号，主编号在路径中。
 */
public record MergeAnimalRequest(List<String> sourceCodes, String reason) {

    public AnimalMergeCommand toCommand(String mainCode) {
        return new AnimalMergeCommand(mainCode, sourceCodes, reason);
    }
}
