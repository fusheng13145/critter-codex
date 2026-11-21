package com.just.critter.controller.archive;

import com.just.critter.service.archive.SaveSpeciesCommand;

/**
 * 物种新建/修改请求。PUT 时所有字段都可选，但至少要传一个。
 */
public record SaveSpeciesRequest(
        String commonName,
        String scientificName,
        String category,
        String protectionLevel,
        String note) {

    public SaveSpeciesCommand toCommand() {
        return new SaveSpeciesCommand(commonName, scientificName, category, protectionLevel, note);
    }
}
