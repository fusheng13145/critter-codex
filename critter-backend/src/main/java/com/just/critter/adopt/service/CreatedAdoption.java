package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;

/**
 * 提交成功后的最小回执，不暴露数据库主键。
 */
public record CreatedAdoption(
        String adoptionNo,
        AdoptionStatus status,
        int amount,
        AdoptionPeriod period) {
}
