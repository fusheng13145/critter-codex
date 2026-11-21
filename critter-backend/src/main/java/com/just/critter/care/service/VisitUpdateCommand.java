package com.just.critter.care.service;

import java.util.List;

/**
 * 管理员纠错就诊的输入。reason 不落 visit 表，只写审计日志。
 */
public record VisitUpdateCommand(
        String complaint,
        String diagnosis,
        String treatment,
        Integer cost,
        List<String> fileIds,
        String reason) {
}
