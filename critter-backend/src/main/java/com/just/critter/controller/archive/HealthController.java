package com.just.critter.controller.archive;

import com.just.critter.service.archive.CreatedHealthRecord;
import com.just.critter.service.archive.HealthRecordView;
import com.just.critter.service.archive.HealthService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健康与防疫接口（03 §6.2 #9—#10）。GET 登录可见、POST 管理员；
 * 操作人取当前登录上下文，不再信任客户端头。
 */
@RestController
@RequestMapping("/api/v1/animals/{code}/health")
public class HealthController {

    private final HealthService service;

    public HealthController(HealthService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<HealthRecordView>> list(@PathVariable String code) {
        return Result.ok(service.list(code));
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<CreatedHealthRecord> create(@PathVariable String code,
                                              @RequestBody CreateHealthRecordRequest request) {
        return Result.ok(service.create(code, request.toCommand(), AuthContext.require().id()));
    }
}
