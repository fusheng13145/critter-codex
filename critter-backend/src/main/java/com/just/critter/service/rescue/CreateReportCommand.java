package com.just.critter.service.rescue;

/**
 * 学生上报入参。type 保留原始字符串，由服务层转枚举并回 30303，避免 Jackson 提前报 30304。
 */
public record CreateReportCommand(
        String type,
        Long pointId,
        java.util.List<String> photos,
        String description,
        String animalCode,
        String contact) {
}
