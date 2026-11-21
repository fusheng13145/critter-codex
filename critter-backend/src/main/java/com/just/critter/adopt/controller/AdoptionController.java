package com.just.critter.adopt.controller;

import com.just.critter.adopt.service.AdoptionApplicationService;
import com.just.critter.adopt.service.AdoptionAuditService;
import com.just.critter.adopt.service.AuditedAdoption;
import com.just.critter.adopt.service.CreatedAdoption;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认养申请与审核接口。身份只从鉴权上下文读取，不接收可伪造的 X-User-Id。
 */
@RestController
@RequestMapping("/api/v1/adoptions")
public class AdoptionController {

    private final AdoptionApplicationService applicationService;
    private final AdoptionAuditService auditService;

    public AdoptionController(AdoptionApplicationService applicationService,
                              AdoptionAuditService auditService) {
        this.applicationService = applicationService;
        this.auditService = auditService;
    }

    @PostMapping
    public Result<CreatedAdoption> create(@Valid @RequestBody CreateAdoptionRequest request) {
        long userId = AuthContext.require().id();
        return Result.ok(applicationService.create(request.toCommand(), userId));
    }

    @PutMapping("/{no}/audit")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<AuditedAdoption> audit(@PathVariable String no,
                                         @Valid @RequestBody AdoptionAuditRequest request) {
        long auditorId = AuthContext.require().id();
        return Result.ok(auditService.audit(no, request.toCommand(), auditorId));
    }
}
