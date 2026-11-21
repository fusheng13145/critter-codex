package com.just.critter.domain.model;

import lombok.Data;

@Data
public class ClinicRow {
    private Long id;
    private String clinicName;
    private String contact;
    private String phone;
    private String address;
    private String priceNote;
}
