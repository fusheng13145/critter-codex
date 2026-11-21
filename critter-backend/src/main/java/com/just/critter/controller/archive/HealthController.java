package com.just.critter.controller.archive;

import com.just.critter.service.archive.CreatedHealthRecord;
import com.just.critter.service.archive.HealthRecordView;
import com.just.critter.service.archive.HealthService;
import com.just.critter.support.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健康与防疫接口（03 §6.2 #9—#10）。GET 登录可见、POST 管理员，
 * 鉴权落地前仍用 X-User-Id 占位，不能视为可信身份。
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
    public Result<CreatedHealthRecord> create(@PathVariable String code,
                                              @RequestBody CreateHealthRecordRequest request,
                                              @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.create(code, request.toCommand(), userId));
    }
}
