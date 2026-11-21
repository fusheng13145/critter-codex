package com.just.critter.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdoptionPaymentRow {
    private String paymentNo;
    private Integer amount;
    private LocalDateTime paidAt;
    private String voucherFileIds;
    private String note;
    private LocalDateTime verifiedAt;
}
