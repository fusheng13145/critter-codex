package com.just.critter.adopt.controller;

import com.just.critter.adopt.service.AdoptionLifecycleService;
import com.just.critter.adopt.service.TerminatedAdoption;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adoptions")
public class AdoptionLifecycleController {

    private final AdoptionLifecycleService service;

    public AdoptionLifecycleController(AdoptionLifecycleService service) {
        this.service = service;
    }

    @PutMapping("/{no}/terminate")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<TerminatedAdoption> terminate(@PathVariable String no,
                                                @RequestBody AdoptionTerminateRequest request) {
        return Result.ok(service.terminate(no, request.reason()));
    }
}
