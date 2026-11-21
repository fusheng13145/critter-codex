package com.just.critter.care.service;

public record ReferenceClinic(
        long id,
        String clinicName,
        String contact,
        String phone,
        String address,
        String priceNote) {
}
