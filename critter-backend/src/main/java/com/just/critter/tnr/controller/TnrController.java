package com.just.critter.tnr.controller;

import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import com.just.critter.tnr.service.TnrService;
import com.just.critter.tnr.service.TnrTaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TNR 任务看板与五节点推进，接口编号 39–41。
 */
@RestController
@RequestMapping("/api/v1/tnr")
public class TnrController {

    private final TnrService service;

    public TnrController(TnrService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<TnrTaskView>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String animalCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(stage, animalCode, new PageQuery(page, size)));
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<TnrTaskView> create(@RequestBody TnrCreateRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }

    @PutMapping("/{no}/node")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<TnrTaskView> advance(@PathVariable String no,
                                       @RequestBody TnrNodeRequest request) {
        return Result.ok(service.advance(no, request.toCommand(), AuthContext.require().id()));
    }
}
