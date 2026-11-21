package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionStatus;

import java.time.LocalDate;

public record TerminatedAdoption(
        String adoptionNo,
        AdoptionStatus status,
        LocalDate endDate,
        String animalStatus) {
}
