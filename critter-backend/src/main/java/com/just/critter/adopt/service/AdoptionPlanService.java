package com.just.critter.adopt.service;

import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.repository.AdoptionPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdoptionPlanService {

    private final AdoptionPlanRepository adoptionPlanRepository;

    public AdoptionPlanService(AdoptionPlanRepository adoptionPlanRepository) {
        this.adoptionPlanRepository = adoptionPlanRepository;
    }

    public List<AdoptionPlan> listEnabledPlans() {
        return adoptionPlanRepository.findAll().stream()
                .filter(AdoptionPlan::enabled)
                .toList();
    }
}
