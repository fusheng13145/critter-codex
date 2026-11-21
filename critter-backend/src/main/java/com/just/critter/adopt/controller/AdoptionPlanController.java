package com.just.critter.adopt.controller;

import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanCommand;
import com.just.critter.adopt.service.AdoptionPlanService;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public Result<List<AdoptionPlanResponse>> listPlans(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        if (includeDisabled) {
            requireAdmin();
        }
        List<AdoptionPlan> plans = includeDisabled
                ? adoptionPlanService.listPlans(true)
                : adoptionPlanService.listEnabledPlans();
        return Result.ok(plans.stream().map(AdoptionPlanResponse::from).toList());
    }

    @PostMapping
    public Result<AdoptionPlanResponse> create(@RequestBody AdoptionPlanRequest request) {
        CurrentUser current = requireAdmin();
        AdoptionPlan plan = adoptionPlanService.create(toCommand(request), current.id());
        return Result.ok(AdoptionPlanResponse.from(plan));
    }

    @PutMapping("/{id}")
    public Result<AdoptionPlanResponse> update(@PathVariable long id,
                                               @RequestBody AdoptionPlanRequest request) {
        CurrentUser current = requireAdmin();
        AdoptionPlan plan = adoptionPlanService.update(id, toCommand(request), current.id());
        return Result.ok(AdoptionPlanResponse.from(plan));
    }

    private CurrentUser requireAdmin() {
        CurrentUser current = AuthContext.require();
        if (!(current.isAdmin() || current.isSystemAdmin())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        return current;
    }

    private AdoptionPlanCommand toCommand(AdoptionPlanRequest request) {
        return new AdoptionPlanCommand(request.planName(), request.period(),
                request.amount(), request.benefits(), request.enabled());
    }
}
