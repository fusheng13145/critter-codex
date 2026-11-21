package com.just.critter.care.service;

/**
 * 关联工单快照：animal_id 必须与就诊动物一致。
 */
public record ReferenceOrder(long id, String orderNo, Long animalId) {
}
