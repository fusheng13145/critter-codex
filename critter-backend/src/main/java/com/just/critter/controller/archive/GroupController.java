package com.just.critter.controller.archive;

import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.service.archive.CreatedGroup;
import com.just.critter.service.archive.CreatedSighting;
import com.just.critter.service.archive.GroupDetail;
import com.just.critter.service.archive.GroupFilter;
import com.just.critter.service.archive.GroupService;
import com.just.critter.service.archive.GroupSummary;
import com.just.critter.service.archive.SightingFilter;
import com.just.critter.service.archive.SightingView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 群体与目击接口（03 §6.2 #11—#19 的 P0 部分：建档、列表、详情、编辑、目击登记与列表）。
 * 归并、拆分、归属修正为 P1，后续迭代补。
 */
@RestController
@RequestMapping("/api/v1")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @PostMapping("/groups")
    public Result<CreatedGroup> create(@RequestBody CreateGroupRequest request,
                                       @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.create(request.toCommand(), userId));
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
    public Result<GroupDetail> update(@PathVariable String code,
                                      @RequestBody UpdateGroupRequest request,
                                      @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.update(code, request.toCommand(), userId));
    }

    @PostMapping("/groups/{code}/sightings")
    public Result<CreatedSighting> recordSighting(@PathVariable String code,
                                                  @RequestBody CreateSightingRequest request,
                                                  @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.recordSighting(code, request.toCommand(), userId));
    }

    @GetMapping("/sightings")
    public Result<PageResult<SightingView>> listSightings(
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String assignStatus,
            @RequestParam(required = false) Long pointId,
            PageQuery pageQuery) {
        return Result.ok(service.searchSightings(new SightingFilter(groupCode, assignStatus, pointId), pageQuery));
    }
}
