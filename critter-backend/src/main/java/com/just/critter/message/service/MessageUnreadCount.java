package com.just.critter.message.service;

public record MessageUnreadCount(
        long adoptionExpiry,
        long donationProgress,
        long orderProgress,
        long approvalResult) {
}
