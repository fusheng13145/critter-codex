package com.just.critter.care.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 就诊纠错的可编辑字段快照。动物、医院与到院时间不允许通过纠错修改。
 */
public record VisitUpdateWrite(
        String complaint,
        String diagnosis,
        String treatment,
        int cost,
        List<String> fileIds,
        LocalDateTime updatedAt) {
}
