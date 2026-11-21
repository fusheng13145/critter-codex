package com.just.critter.controller.archive;

import com.just.critter.service.archive.SavePointCommand;

import java.math.BigDecimal;

/**
 * 点位新建/修改请求。PUT 时所有字段都可选，但至少要传一个。
 */
public record SavePointRequest(
        String pointName,
        String area,
        BigDecimal x,
        BigDecimal y,
        Boolean enabled,
        String note) {

    public SavePointCommand toCommand() {
        return new SavePointCommand(pointName, area, x, y, enabled, note);
    }
}
