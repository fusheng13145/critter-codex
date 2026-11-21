package com.just.critter.service.archive;

import java.math.BigDecimal;

/**
 * 点位写入命令。enabled=false 表示停用，不物理删除。
 */
public record SavePointCommand(
        String pointName,
        String area,
        BigDecimal x,
        BigDecimal y,
        Boolean enabled,
        String note) {
}
