package com.just.critter.care.service;

/**
 * 管理员新增合作医院的输入。可选字段为空或空白时按未填写处理。
 */
public record ClinicCommand(
        String clinicName,
        String contact,
        String phone,
        String address,
        String priceNote) {
}
