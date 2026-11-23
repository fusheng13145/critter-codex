package com.just.critter.care.plan;

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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/care/plans")
public class CarePlanController {

    private final CarePlanService plans;

    public CarePlanController(CarePlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public Result<PageResult<CarePlanView>> list(
            @RequestParam(required = false) String animalCode,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(plans.list(animalCode, type, status, new PageQuery(page, size)));
    }

    @GetMapping("/overdue")
    public Result<List<CarePlanView>> overdue(@RequestParam(defaultValue = "3") int limit) {
        return Result.ok(plans.overdue(limit));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PostMapping
    public Result<CarePlanView> create(@RequestBody(required = false) CarePlanCreateRequest request) {
        return Result.ok(plans.create(request == null ? null : request.toCommand(),
                AuthContext.require().id()));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/{id}/complete")
    public Result<CarePlanView> complete(@PathVariable long id,
                                         @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> safe = body == null ? Map.of() : body;
        return Result.ok(plans.complete(id, safe.get("result"), safe.get("note"),
                AuthContext.require().id()));
    }

    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN", "SYS_ADMIN"})
    @PutMapping("/{id}/cancel")
    public Result<CarePlanView> cancel(@PathVariable long id,
                                       @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> safe = body == null ? Map.of() : body;
        return Result.ok(plans.cancel(id, safe.get("note"), AuthContext.require().id()));
    }
}
