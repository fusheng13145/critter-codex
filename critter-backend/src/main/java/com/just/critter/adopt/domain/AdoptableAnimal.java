package com.just.critter.adopt.domain;

import com.just.critter.domain.enums.AnimalStatus;

/**
 * 申请时对动物档案的最小快照。仅用于状态判断，不承载展示字段。
 */
public record AdoptableAnimal(long id, String code, AnimalStatus status) {
}
