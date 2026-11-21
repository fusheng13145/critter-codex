package com.just.critter.care.controller;

import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.care.service.VisitCommand;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visits")
public class VisitController {

    private final VisitService visitService;
    private final MedicalQueryService queryService;

    public VisitController(VisitService visitService, MedicalQueryService queryService) {
        this.visitService = visitService;
        this.queryService = queryService;
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<VisitRegistration> register(@RequestBody VisitCreateRequest request) {
        return Result.ok(visitService.register(new VisitCommand(
                request.animalId(), request.orderId(), request.clinicId(), request.visitedAt(),
                request.complaint(), request.diagnosis(), request.treatment(), request.cost(),
                request.fileIds(), request.sourceNote()), AuthContext.require().id()));
    }

    @GetMapping("/{no}")
    public Result<VisitDetail> detail(@PathVariable String no) {
        return Result.ok(queryService.visit(no));
    }
}
