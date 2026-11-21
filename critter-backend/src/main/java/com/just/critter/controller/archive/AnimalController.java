package com.just.critter.controller.archive;

import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.service.archive.AnimalChangeLogView;
import com.just.critter.service.archive.AnimalDetail;
import com.just.critter.service.archive.AnimalFilter;
import com.just.critter.service.archive.AnimalMergeService;
import com.just.critter.service.archive.AnimalMergeResult;
import com.just.critter.service.archive.AnimalQrCodeService;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.AnimalStatusLogView;
import com.just.critter.service.archive.AnimalStatusResult;
import com.just.critter.service.archive.AnimalStatusService;
import com.just.critter.service.archive.AnimalSummary;
import com.just.critter.service.archive.CreatedAnimal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

/**
 * 档案接口（03 §6.2 #1—#6）。列表与公开详情匿名可看，
 * 建档、编辑、变更历史要求管理员；操作人取当前登录上下文，不再信任客户端头。
 */
@RestController
@RequestMapping("/api/v1/animals")
public class AnimalController {

    private final AnimalService service;
    private final AnimalStatusService statusService;
    private final AnimalMergeService mergeService;
    private final AnimalQrCodeService qrCodeService;

    public AnimalController(AnimalService service, AnimalStatusService statusService,
                            AnimalMergeService mergeService, AnimalQrCodeService qrCodeService) {
        this.service = service;
        this.statusService = statusService;
        this.mergeService = mergeService;
        this.qrCodeService = qrCodeService;
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<CreatedAnimal> create(@RequestBody CreateAnimalRequest request) {
        return Result.ok(service.create(request.toCommand(), AuthContext.require().id()));
    }

    @GetMapping
    public Result<PageResult<AnimalSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long pointId,
            @RequestParam(required = false) Boolean neutered,
            @RequestParam(required = false) Boolean adopted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AnimalFilter filter = new AnimalFilter(keyword, parseStatuses(status), pointId, neutered, adopted);
        return Result.ok(service.search(filter, new PageQuery(page, size)));
    }

    @GetMapping("/{code}")
    public Result<AnimalDetail> detail(@PathVariable String code) {
        var current = AuthContext.get();
        return Result.ok(service.detail(code, current != null && current.isAdmin()));
    }

    @PutMapping("/{code}")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<AnimalDetail> update(@PathVariable String code,
                                       @RequestBody UpdateAnimalRequest request) {
        return Result.ok(service.update(code, request.toCommand(), AuthContext.require().id()));
    }

    @PutMapping("/{code}/status")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<AnimalStatusResult> changeStatus(@PathVariable String code,
                                                   @RequestBody ChangeAnimalStatusRequest request) {
        return Result.ok(statusService.change(code, request.toCommand(), AuthContext.require().id()));
    }

    @GetMapping("/{code}/status-history")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<List<AnimalStatusLogView>> statusHistory(@PathVariable String code) {
        return Result.ok(statusService.history(code));
    }

    @GetMapping("/{code}/history")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<List<AnimalChangeLogView>> history(@PathVariable String code) {
        return Result.ok(service.history(code));
    }

    @PostMapping("/{code}/merge")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public Result<AnimalMergeResult> merge(@PathVariable String code,
                                          @RequestBody(required = false) MergeAnimalRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(mergeService.merge(request.toCommand(code), AuthContext.require().id()));
    }

    @GetMapping("/{code}/qrcode")
    @RequireRoles({"ANIMAL_ADMIN", "SYSTEM_ADMIN"})
    public ResponseEntity<byte[]> qrcode(@PathVariable String code) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCodeService.png(code));
    }

    private List<AnimalStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return AnimalStatus.valueOf(value);
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(30303, "状态值非法：" + value);
                    }
                })
                .toList();
    }
}
