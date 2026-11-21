package com.just.critter.adopt.service;

import java.time.LocalDate;

public record CertificateView(
        String adoptionNo,
        String certificateNo,
        String adopterName,
        String animalCode,
        String animalName,
        String planName,
        LocalDate startDate,
        LocalDate endDate) {
}
