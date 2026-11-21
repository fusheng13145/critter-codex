package com.just.critter.care.controller;

import com.just.critter.care.service.ClinicCommand;

/**
 * 新增合作医院请求。可选字段允许省略。
 */
public record ClinicCreateRequest(
        String clinicName,
        String contact,
        String phone,
        String address,
        String priceNote) {

    public ClinicCommand toCommand() {
        return new ClinicCommand(clinicName, contact, phone, address, priceNote);
    }
}
