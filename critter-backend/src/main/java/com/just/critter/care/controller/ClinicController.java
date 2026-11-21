package com.just.critter.care.controller;

import com.just.critter.care.service.ClinicService;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final MedicalQueryService service;
    private final ClinicService clinicService;

    public ClinicController(MedicalQueryService service, ClinicService clinicService) {
        this.service = service;
        this.clinicService = clinicService;
    }

    @GetMapping
    public Result<List<ClinicView>> list() {
        return Result.ok(service.clinics());
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<ClinicView> create(@RequestBody ClinicCreateRequest request) {
        return Result.ok(clinicService.create(request.toCommand(), AuthContext.require().id()));
    }
}
