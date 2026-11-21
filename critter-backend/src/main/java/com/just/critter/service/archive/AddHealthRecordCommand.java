package com.just.critter.service.archive;

import com.just.critter.domain.enums.HealthType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 新增健康记录命令。recordedAt 必须带时区，落库前统一换算到 Asia/Shanghai。
 */
public record AddHealthRecordCommand(
        HealthType type,
        OffsetDateTime recordedAt,
        String result,
        LocalDate nextDate) {
}
