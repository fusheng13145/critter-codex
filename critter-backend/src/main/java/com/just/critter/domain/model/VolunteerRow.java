package com.just.critter.domain.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VolunteerRow {

    private String status;
    private BigDecimal totalHours;
}
