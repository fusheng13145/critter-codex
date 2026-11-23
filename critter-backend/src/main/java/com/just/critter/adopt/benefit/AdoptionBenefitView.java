package com.just.critter.adopt.benefit;

import java.time.LocalDate;
import java.util.List;

public record AdoptionBenefitView(
        String adoptionNo,
        String animalCode,
        String animalName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        List<AdoptionPostView> posts,
        List<AdoptionVisitView> visits) {
}
