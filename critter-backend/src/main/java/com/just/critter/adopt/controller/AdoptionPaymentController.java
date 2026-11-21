package com.just.critter.adopt.controller;

import com.just.critter.adopt.service.AdoptionPaymentResult;
import com.just.critter.adopt.service.AdoptionPaymentService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adoptions")
public class AdoptionPaymentController {

    private final AdoptionPaymentService service;

    public AdoptionPaymentController(AdoptionPaymentService service) {
        this.service = service;
    }

    @PutMapping("/{no}/payment")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<AdoptionPaymentResult> pay(@PathVariable String no,
                                             @RequestBody AdoptionPaymentRequest request) {
        return Result.ok(service.pay(no, request.toCommand(), AuthContext.require().id()));
    }
}
