package com.just.critter.controller.archive;

import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.service.archive.AnimalChangeLogView;
import com.just.critter.service.archive.AnimalDetail;
import com.just.critter.service.archive.AnimalFilter;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.AnimalSummary;
import com.just.critter.service.archive.CreatedAnimal;
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

import java.util.Arrays;
import java.util.List;

/**
 * 档案接口（03 §6.2 #1—#6）。权限注解等 A 的鉴权落地后再补，
 * 当前用 X-User-Id 占位，禁止把该头当成可信身份对外暴露。
 */
@RestController
@RequestMapping("/api/v1/animals")
public class AnimalController {

    private final AnimalService service;

    public AnimalController(AnimalService service) {
        this.service = service;
    }

    @PostMapping
    public Result<CreatedAnimal> create(@RequestBody CreateAnimalRequest request,
                                        @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.create(request.toCommand(), userId));
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
        return Result.ok(service.detail(code, false));
    }

    @PutMapping("/{code}")
    public Result<AnimalDetail> update(@PathVariable String code,
                                       @RequestBody UpdateAnimalRequest request,
                                       @RequestHeader(value = "X-User-Id", defaultValue = "0") Long userId) {
        return Result.ok(service.update(code, request.toCommand(), userId));
    }

    @GetMapping("/{code}/history")
    public Result<List<AnimalChangeLogView>> history(@PathVariable String code) {
        return Result.ok(service.history(code));
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
