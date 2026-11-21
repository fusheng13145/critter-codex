package com.just.critter.service.archive;

/**
 * 物种写入命令。
 */
public record SaveSpeciesCommand(
        String commonName,
        String scientificName,
        String category,
        String protectionLevel,
        String note) {
}
