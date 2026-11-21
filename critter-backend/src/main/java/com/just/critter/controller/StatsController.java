package com.just.critter.controller;

import com.just.critter.controller.dto.StatsDtos;
import com.just.critter.service.stats.StatsService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 统计接口（接口设计 §6.3 第 40—42 项）。
 * 权限按文档：生物总览登录可见；资金与效率只给管理员（系统管理员可读，不参与复核）。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/biology")
    public Result<StatsDtos.BiologyResponse> biology() {
        return Result.ok(statsService.biology());
    }

    @GetMapping("/fund")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<StatsDtos.FundResponse> fund(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statsService.fund(from, to));
    }

    @GetMapping("/efficiency")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<StatsDtos.EfficiencyResponse> efficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(statsService.efficiency(from, to));
    }
}
