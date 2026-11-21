package com.just.critter.care.controller;

import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.support.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final MedicalQueryService service;

    public ClinicController(MedicalQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<ClinicView>> list() {
        return Result.ok(service.clinics());
    }
}
