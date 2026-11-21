package com.just.critter.adopt.service;

import java.time.LocalDateTime;
import java.util.List;

public record AdoptionPaymentWrite(
        String paymentNo,
        long adoptionId,
        int amount,
        LocalDateTime paidAt,
        long verifierId,
        LocalDateTime verifiedAt,
        List<String> voucherFileIds,
        String note) {
}
