package com.just.critter.adopt.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record AdoptionPaymentCommand(
        int paidAmount,
        OffsetDateTime paidAt,
        List<String> voucherFileIds,
        String note) {
}
