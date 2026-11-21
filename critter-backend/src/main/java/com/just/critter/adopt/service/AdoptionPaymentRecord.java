package com.just.critter.adopt.service;

import java.time.LocalDateTime;
import java.util.List;

public record AdoptionPaymentRecord(
        String paymentNo,
        int paidAmount,
        LocalDateTime paidAt,
        List<String> voucherFileIds,
        String note,
        LocalDateTime verifiedAt) {
}
