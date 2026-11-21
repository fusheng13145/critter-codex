package com.just.critter.adopt.controller;

import com.just.critter.adopt.service.AdoptionDetail;
import com.just.critter.adopt.service.AdoptionQueryService;
import com.just.critter.adopt.service.AdoptionSummary;
import com.just.critter.adopt.service.CertificateView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adoptions")
public class AdoptionQueryController {

    private final AdoptionQueryService service;

    public AdoptionQueryController(AdoptionQueryService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<PageResult<AdoptionSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(status, new PageQuery(page, size)));
    }

    @GetMapping("/mine")
    public Result<PageResult<AdoptionSummary>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = AuthContext.require();
        return Result.ok(service.mine(user.id(), status, new PageQuery(page, size)));
    }

    @GetMapping("/{no}")
    public Result<AdoptionDetail> detail(@PathVariable String no) {
        CurrentUser user = AuthContext.require();
        return Result.ok(service.detail(no, user.id(), isAdmin(user)));
    }

    @GetMapping("/{no}/certificate")
    public Result<CertificateView> certificate(@PathVariable String no) {
        CurrentUser user = AuthContext.require();
        return Result.ok(service.certificate(no, user.id(), isAdmin(user)));
    }

    private boolean isAdmin(CurrentUser user) {
        return user.isAdmin() || user.isSystemAdmin();
    }
}
