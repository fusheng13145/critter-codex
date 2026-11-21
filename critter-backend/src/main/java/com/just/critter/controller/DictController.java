package com.just.critter.controller;

import com.just.critter.controller.dto.DictDtos;
import com.just.critter.service.support.DictService;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.RequireRoles;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 字典接口（接口设计 §6.1 第 21—23 项）。
 * 读取对全部登录用户开放（表单下拉），维护只给系统管理员。
 */
@RestController
@RequestMapping("/api/v1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping
    public Result<DictDtos.DictBundle> get(@RequestParam("type") String type) {
        List<String> types = Arrays.stream(type.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return Result.ok(dictService.getDicts(types));
    }

    @GetMapping("/manage")
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<List<DictDtos.DictView>> manage(@RequestParam(required = false) String type) {
        return Result.ok(dictService.adminList(type));
    }

    @PostMapping
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<DictDtos.DictView> create(@Valid @RequestBody DictDtos.DictCreateRequest request) {
        return Result.ok(dictService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<DictDtos.DictView> update(@PathVariable long id,
                                            @Valid @RequestBody DictDtos.DictUpdateRequest request) {
        return Result.ok(dictService.update(id, request));
    }
}
