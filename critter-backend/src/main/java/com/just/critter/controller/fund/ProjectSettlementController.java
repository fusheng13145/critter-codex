package com.just.critter.controller.fund;

import com.just.critter.service.fund.ProjectSettlementService;
import com.just.critter.service.fund.ProjectSettlementView;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 结项公示：提交仅动物管理员，公示内容公开只读。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectSettlementController {

    private final ProjectSettlementService service;

    public ProjectSettlementController(ProjectSettlementService service) {
        this.service = service;
    }

    @PutMapping("/{projectNo}/settle")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ProjectSettlementView> settle(@PathVariable String projectNo,
                                                @RequestBody(required = false) SettleProjectRequest request) {
        CurrentUser actor = AuthContext.require();
        if (!actor.hasRole("ANIMAL_ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "仅动物管理员可提交结项");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.settle(projectNo, request.toCommand(), actor));
    }

    @GetMapping("/{projectNo}/settlement")
    public Result<ProjectSettlementView> detail(@PathVariable String projectNo) {
        return Result.ok(service.detail(projectNo));
    }

    @PostMapping("/{projectNo}/settlement/notes")
    @RequireRoles({"ANIMAL_ADMIN"})
    public Result<ProjectSettlementView> appendNote(@PathVariable String projectNo,
                                                    @RequestBody(required = false) AppendSettlementNoteRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return Result.ok(service.appendNote(projectNo, request.toCommand(), AuthContext.require()));
    }
}
