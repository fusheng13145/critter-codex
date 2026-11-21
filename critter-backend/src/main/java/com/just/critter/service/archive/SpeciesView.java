package com.just.critter.service.archive;

/**
 * 物种名录视图，保护级别只展示名录值，不在接口层做保护等级推断。
 */
public record SpeciesView(
        Long id,
        String commonName,
        String scientificName,
        String category,
        String protectionLevel,
        String note) {
}
