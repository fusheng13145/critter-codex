package com.just.critter.controller.fund;

import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;
import com.just.critter.service.fund.CreatedProject;
import com.just.critter.service.fund.ProjectAuditResult;
import com.just.critter.service.fund.ProjectDetail;
import com.just.critter.service.fund.ProjectFilter;
import com.just.critter.service.fund.ProjectService;
import com.just.critter.service.fund.ProjectSummary;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.Result;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.RequireRoles;
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
import java.util.Locale;

/**
 * 项目接口：列表/公开详情匿名可看；创建、修改提交、审核仅动物管理员。
 *
 * <p>项目路径同时挂在可选鉴权拦截器下，写接口在控制器内再显式校验一次
 * ANIMAL_ADMIN，避免可选鉴权路径绕过 @RequireRoles。</p>
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<CreatedProject> create(@RequestBody(required = false) CreateProjectRequest request) {
        CurrentUser actor = requireAnimalAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.create(request.toCommand(), actor));
    }

    @GetMapping
    public Result<PageResult<ProjectSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectFilter filter = new ProjectFilter(parseStatuses(status), parseTargetType(targetType), keyword);
        return Result.ok(service.search(filter, new PageQuery(page, size), isAnimalAdmin()));
    }

    @GetMapping("/{projectNo}")
    public Result<ProjectDetail> detail(@PathVariable String projectNo) {
        return Result.ok(service.detail(projectNo, isAnimalAdmin()));
    }

    @PutMapping("/{projectNo}")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ProjectDetail> update(@PathVariable String projectNo,
                                        @RequestBody(required = false) CreateProjectRequest request) {
        CurrentUser actor = requireAnimalAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.update(projectNo, request.toCommand(), actor));
    }

    @PutMapping("/{projectNo}/audit")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ProjectAuditResult> audit(@PathVariable String projectNo,
                                            @RequestBody(required = false) ProjectAuditRequest request) {
        CurrentUser actor = requireAnimalAdmin();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.audit(projectNo, request.toCommand(), actor));
    }

    private boolean isAnimalAdmin() {
        CurrentUser current = AuthContext.get();
        return current != null && current.hasRole("ANIMAL_ADMIN");
    }

    private CurrentUser requireAnimalAdmin() {
        CurrentUser current = AuthContext.require();
        if (!current.hasRole("ANIMAL_ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "仅动物管理员可操作项目");
        }
        return current;
    }

    private List<ProjectStatus> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return ProjectStatus.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new BusinessException(ErrorCode.ENUM_INVALID, "项目状态非法：" + value);
                    }
                })
                .toList();
    }

    private ProjectTargetType parseTargetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ProjectTargetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, "关联对象类型非法：" + raw);
        }
    }
}
