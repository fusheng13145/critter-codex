package com.just.critter.service.archive;

/**
 * 列表与详情里的点位引用，只暴露 id 与名称。
 */
public record PointRef(Long id, String name) {
}
