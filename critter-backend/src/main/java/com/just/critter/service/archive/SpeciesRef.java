package com.just.critter.service.archive;

/**
 * 物种引用，用于群体与出现记录的展示，不带内部备注。
 */
public record SpeciesRef(Long id, String commonName, String category) {
}
