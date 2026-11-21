package com.just.critter.care.controller;

import com.just.critter.care.service.VisitUpdateCommand;

import java.util.List;

/**
 * 纠错就诊请求。reason 必填，只写审计日志，不覆盖来源备注。
 */
public record VisitUpdateRequest(
        String complaint,
        String diagnosis,
        String treatment,
        Integer cost,
        List<String> fileIds,
        String reason) {

    public VisitUpdateCommand toCommand() {
        return new VisitUpdateCommand(complaint, diagnosis, treatment, cost, fileIds, reason);
    }
}
