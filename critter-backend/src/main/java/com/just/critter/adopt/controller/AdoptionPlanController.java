package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class AdoptionPlanController {

    private final AdoptionPlanService adoptionPlanService;

    public AdoptionPlanController(AdoptionPlanService adoptionPlanService) {
        this.adoptionPlanService = adoptionPlanService;
    }

    @PublicEndpoint
    @GetMapping
    public Result<List<AdoptionPlanResponse>> listEnabledPlans() {
        List<AdoptionPlanResponse> plans = adoptionPlanService.listEnabledPlans().stream()
                .map(AdoptionPlanResponse::from)
                .toList();
        return Result.ok(plans);
    }
}
