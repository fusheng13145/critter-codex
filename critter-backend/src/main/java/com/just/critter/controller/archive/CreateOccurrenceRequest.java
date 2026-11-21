package com.just.critter.controller.archive;

import com.just.critter.domain.enums.OccurrenceScale;
import com.just.critter.service.archive.CreateOccurrenceCommand;
import com.just.critter.support.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 出现记录请求。scale 用 Object 接收：字符串按枚举解析，数字直接 30303，
 * 避免 Jackson 把数字强转成字符串或抛反序列化异常。
 */
public record CreateOccurrenceRequest(
        Long speciesId,
        Long pointId,
        OffsetDateTime occurredAt,
        Object scale,
        String habitat,
        List<String> fileIds) {

    public CreateOccurrenceCommand toCommand() {
        OccurrenceScale parsed = null;
        if (scale != null) {
            if (!(scale instanceof String text)) {
                throw new BusinessException(30303, "出现数量只能使用枚举：FEW / SMALL_HERD / LARGE_HERD");
            }
            try {
                parsed = OccurrenceScale.valueOf(text.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(30303, "出现数量只能使用枚举：FEW / SMALL_HERD / LARGE_HERD");
            }
        }
        return new CreateOccurrenceCommand(speciesId, pointId, occurredAt, parsed, habitat, fileIds);
    }
}
