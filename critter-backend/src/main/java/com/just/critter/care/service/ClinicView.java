package com.just.critter.care.service;

/**
 * 合作医院视图：price_note 仅作内部示例报价参考，不对外承诺真实价格。
 */
public record ClinicView(
        long clinicId,
        String clinicName,
        String contact,
        String phone,
        String address,
        String priceNote) {
}
