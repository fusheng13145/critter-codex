package com.just.critter.tnr.service;

import java.util.Optional;

/**
 * TNR 五节点顺序：抓捕 → 送医 → 手术 → 放归 → 观察。
 */
public enum TnrStage {
    CATCH,
    TRANSPORT,
    SURGERY,
    RELEASE,
    OBSERVE;

    public Optional<TnrStage> next() {
        return switch (this) {
            case CATCH -> Optional.of(TRANSPORT);
            case TRANSPORT -> Optional.of(SURGERY);
            case SURGERY -> Optional.of(RELEASE);
            case RELEASE -> Optional.of(OBSERVE);
            case OBSERVE -> Optional.empty();
        };
    }

    public static Optional<TnrStage> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
