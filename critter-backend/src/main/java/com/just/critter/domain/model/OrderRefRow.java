package com.just.critter.domain.model;

import lombok.Data;

@Data
public class OrderRefRow {
    private Long id;
    private String orderNo;
    private Long animalId;
}
