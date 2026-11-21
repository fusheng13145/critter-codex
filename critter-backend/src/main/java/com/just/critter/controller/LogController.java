package com.just.critter.controller;

import com.just.critter.controller.dto.LogDtos;
import com.just.critter.service.support.LogService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 操作日志检索（P10 日志页），仅系统管理员。 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<LogDtos.LogPage> query(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(logService.query(actorId, action, bizType, bizId, from, to, result,
                new PageQuery(page, size)));
    }
}
