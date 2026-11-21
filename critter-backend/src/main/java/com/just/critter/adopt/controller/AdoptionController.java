package com.just.critter.adopt.controller;

import com.just.critter.adopt.service.AdoptionApplicationService;
import com.just.critter.adopt.service.CreatedAdoption;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认养申请接口。身份只从鉴权上下文读取，不接收可伪造的 X-User-Id。
 */
@RestController
@RequestMapping("/api/v1/adoptions")
public class AdoptionController {

    private final AdoptionApplicationService service;

    public AdoptionController(AdoptionApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public Result<CreatedAdoption> create(@Valid @RequestBody CreateAdoptionRequest request) {
        long userId = AuthContext.require().id();
        return Result.ok(service.create(request.toCommand(), userId));
    }
}
