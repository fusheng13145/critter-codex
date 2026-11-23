package com.just.critter.adopt.benefit;

import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
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
@RequestMapping("/api/v1/adoptions")
public class AdoptionBenefitController {

    private final AdoptionBenefitService benefits;

    public AdoptionBenefitController(AdoptionBenefitService benefits) {
        this.benefits = benefits;
    }

    @GetMapping("/{no}/benefits")
    public Result<AdoptionBenefitView> view(@PathVariable String no) {
        return Result.ok(benefits.view(no, AuthContext.require().id()));
    }

    @PostMapping("/{no}/posts")
    public Result<AdoptionPostView> post(@PathVariable String no,
                                         @RequestBody(required = false) AdoptionPostCreateRequest request) {
        return Result.ok(benefits.addPost(no, AuthContext.require().id(),
                request == null ? null : request.title(),
                request == null ? null : request.content()));
    }

    @PostMapping("/{no}/visits")
    public Result<AdoptionVisitView> bookVisit(@PathVariable String no,
                                              @RequestBody(required = false) VisitBookingRequest request) {
        return Result.ok(benefits.bookVisit(no, AuthContext.require().id(),
                request == null ? null : request.toCommand()));
    }

    @PutMapping("/{no}/visits/{visitId}/cancel")
    public Result<AdoptionVisitView> cancelVisit(@PathVariable String no,
                                                 @PathVariable long visitId) {
        return Result.ok(benefits.cancelVisit(no, AuthContext.require().id(), visitId));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @GetMapping("/visits/pending")
    public Result<List<AdoptionBenefitRepository.VisitAdminView>> pendingVisits(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(benefits.pendingVisits(limit));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/{no}/visits/{visitId}/review")
    public Result<AdoptionVisitView> review(@PathVariable String no,
                                            @PathVariable long visitId,
                                            @RequestBody(required = false) VisitReviewRequest request) {
        return Result.ok(benefits.review(no, visitId,
                request == null ? null : request.decision(),
                request == null ? null : request.reason(),
                AuthContext.require().id()));
    }
}
