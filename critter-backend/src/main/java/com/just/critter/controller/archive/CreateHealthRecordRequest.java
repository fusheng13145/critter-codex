package com.just.critter.controller.archive;

import com.just.critter.domain.enums.HealthType;
import com.just.critter.service.archive.AddHealthRecordCommand;
import com.just.critter.support.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 健康记录请求体。type 用字符串接收后再解析，保证非法枚举统一返回 30303，
 * 而不是被 Jackson 反序列化异常吞成 50001。
 */
public record CreateHealthRecordRequest(
        String type,
        OffsetDateTime recordedAt,
        String result,
        LocalDate nextDate) {

    public AddHealthRecordCommand toCommand() {
        HealthType parsed = null;
        if (type != null) {
            try {
                parsed = HealthType.valueOf(type.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(30303, "健康记录类型非法：" + type);
            }
        }
        return new AddHealthRecordCommand(parsed, recordedAt, result, nextDate);
    }
}
