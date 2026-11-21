package com.just.critter.service.archive;

import com.just.critter.domain.enums.AnimalStatus;

import java.time.OffsetDateTime;

/**
 * 建档结果，对应 03 §7.3 响应 data。
 */
public record CreatedAnimal(String code, AnimalStatus status, OffsetDateTime createdAt) {
}
