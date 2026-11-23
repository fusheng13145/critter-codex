package com.just.critter.adopt.handover;

import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
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

@RestController
@RequestMapping("/api/v1/handovers")
@RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
public class HandoverController {

    private final HandoverService handovers;

    public HandoverController(HandoverService handovers) {
        this.handovers = handovers;
    }

    @GetMapping
    public Result<PageResult<HandoverView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(handovers.list(status, new PageQuery(page, size)));
    }

    @GetMapping("/{no}")
    public Result<HandoverDetail> detail(@PathVariable String no) {
        return Result.ok(handovers.detail(no));
    }

    @PostMapping
    public Result<HandoverDetail> create(@RequestBody(required = false) HandoverCreateRequest request) {
        return Result.ok(handovers.create(request == null ? null : request.toCommand(),
                AuthContext.require().id()));
    }

    @PutMapping("/{no}/followups/{seqNo}/complete")
    public Result<FollowupView> complete(@PathVariable String no, @PathVariable int seqNo,
                                         @RequestBody(required = false) FollowupCompleteRequest request) {
        return Result.ok(handovers.complete(no, seqNo,
                request == null ? null : request.toCommand(), AuthContext.require().id()));
    }
}
