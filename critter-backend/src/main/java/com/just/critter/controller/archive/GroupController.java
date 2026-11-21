package com.just.critter.controller.archive;

import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.service.archive.CreatedGroup;
import com.just.critter.service.archive.CreatedSighting;
import com.just.critter.service.archive.GroupDetail;
import com.just.critter.service.archive.GroupFilter;
import com.just.critter.service.archive.GroupMaintenanceService;
import com.just.critter.service.archive.GroupMergeResult;
import com.just.critter.service.archive.GroupService;
import com.just.critter.service.archive.GroupSplitResult;
import com.just.critter.service.archive.GroupSummary;
import com.just.critter.service.archive.SightingFilter;
import com.just.critter.service.archive.SightingView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
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

/**
 * 群体与目击接口（03 §6.2 #11—#19）。P1 的合并、拆分、目击归属修正也在本控制器。
 */
@RestController
@RequestMapping("/api/v1")
public class GroupController {

    private final GroupService service;
    private final GroupMaintenanceService maintenance;

    public GroupController(GroupService service, GroupMaintenanceService maintenance) {
        this.service = service;
        this.maintenance = maintenance;
    }

    @PostMapping("/groups")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<CreatedGroup> create(@RequestBody CreateGroupRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }

    @GetMapping("/groups")
    public Result<PageResult<GroupSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long speciesId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long pointId,
            PageQuery pageQuery) {
        GroupStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = GroupStatus.valueOf(status.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(30303, "群体状态非法：" + status);
            }
        }
        return Result.ok(service.search(new GroupFilter(keyword, speciesId, parsed, pointId), pageQuery));
    }

    @GetMapping("/groups/{code}")
    public Result<GroupDetail> detail(@PathVariable String code) {
        return Result.ok(service.detail(code));
    }

    @PutMapping("/groups/{code}")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<GroupDetail> update(@PathVariable String code,
                                      @RequestBody UpdateGroupRequest request) {
        return Result.ok(service.update(code, request.toCommand(), AuthContext.require().id()));
    }

    @PostMapping("/groups/{code}/sightings")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<CreatedSighting> recordSighting(@PathVariable String code,
                                                  @RequestBody CreateSightingRequest request) {
        return Result.ok(service.recordSighting(code, request.toCommand(), AuthContext.require().id()));
    }

    @PostMapping("/groups/merge")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<GroupMergeResult> merge(@RequestBody GroupMergeRequest request) {
        return Result.ok(maintenance.merge(request.toCommand(), AuthContext.require().id()));
    }

    @PostMapping("/groups/{code}/split")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<GroupSplitResult> split(@PathVariable String code,
                                          @RequestBody SplitGroupRequest request) {
        return Result.ok(maintenance.split(request.toCommand(code), AuthContext.require().id()));
    }

    @GetMapping("/sightings")
    public Result<PageResult<SightingView>> listSightings(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String assignStatus,
            @RequestParam(required = false) Long pointId,
            PageQuery pageQuery) {
        return Result.ok(service.searchSightings(new SightingFilter(groupCode, assignStatus, pointId), pageQuery));
    }

    @PutMapping("/sightings/{id}")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<SightingView> assignSighting(@PathVariable Long id,
                                               @RequestBody UpdateSightingRequest request) {
        return Result.ok(maintenance.assignSighting(id, request.toCommand(), AuthContext.require().id()));
    }
}
