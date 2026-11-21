package com.just.critter.service.archive;

import java.math.BigDecimal;

/**
 * 点位字典视图，坐标是平面图百分比，前端按底图尺寸换算。
 */
public record PointView(
        Long id,
        String name,
        String area,
        BigDecimal x,
        BigDecimal y,
        boolean enabled,
        String note) {
}
