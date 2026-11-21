package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionPaymentCommand;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public record AdoptionPaymentRequest(
        Integer paidAmount,
        String paidAt,
        List<String> voucherFileIds,
        String note) {

    public AdoptionPaymentCommand toCommand() {
        if (paidAt == null || paidAt.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "paidAt"));
        }
        try {
            return new AdoptionPaymentCommand(
                    paidAmount == null ? 0 : paidAmount,
                    OffsetDateTime.parse(paidAt.trim()),
                    voucherFileIds,
                    note);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "paidAt"));
        }
    }
}
