package com.just.critter.controller.archive;

import com.just.critter.service.archive.PatrolFilter;
import com.just.critter.service.archive.PatrolService;
import com.just.critter.service.archive.PatrolView;
import com.just.critter.service.archive.SightingView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.RequireRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 巡查记录（FR-R-05）：登记、列表、转目击。
 */
@RestController
@RequestMapping("/api/v1/patrols")
public class PatrolController {

    private final PatrolService service;

    public PatrolController(PatrolService service) {
        this.service = service;
    }

    @GetMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<PageResult<PatrolView>> list(
            @RequestParam(required = false) Long pointId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            PageQuery pageQuery) {
        return Result.ok(service.search(new PatrolFilter(pointId, parseTime(from, "from"), parseTime(to, "to")),
                pageQuery));
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<PatrolView> create(@RequestBody CreatePatrolRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }

    @PostMapping("/{id}/convert")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<SightingView> convert(@PathVariable Long id,
                                        @RequestBody(required = false) ConvertPatrolRequest request) {
        return Result.ok(service.convert(id, request == null ? null : request.toCommand(),
                AuthContext.require().id()));
    }

    private OffsetDateTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + " 需为 ISO-8601 时间");
        }
    }
}
