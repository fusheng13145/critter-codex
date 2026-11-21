package com.just.critter.controller;

import com.just.critter.controller.dto.AppealDtos;
import com.just.critter.controller.dto.UserDtos;
import com.just.critter.service.user.AppealService;
import com.just.critter.service.user.UserService;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.Result;
import com.just.critter.support.security.AuthContext;
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

import java.util.List;

/**
 * 用户与身份治理：个人中心（P11）与后台用户页（P10）。
 * 权限口径：本人资料任何登录用户可改；用户列表与禁用只给系统管理员；
 * 身份申请审核给动物管理员与系统管理员（属治理动作，不涉及业务审核权）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AppealService appealService;

    public UserController(UserService userService, AppealService appealService) {
        this.userService = userService;
        this.appealService = appealService;
    }

    @GetMapping("/me")
    public Result<UserDtos.ProfileResponse> me() {
        return Result.ok(userService.profile(AuthContext.require().id()));
    }

    @PutMapping("/me")
    public Result<UserDtos.ProfileResponse> updateMe(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(AuthContext.require().id(), request));
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(AuthContext.require().id(), request);
        return Result.ok();
    }

    @GetMapping("/me/login-logs")
    public Result<List<UserDtos.LoginLogView>> loginLogs(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(userService.myLoginLogs(AuthContext.require().id(), limit));
    }

    @PostMapping("/me/applications")
    public Result<UserDtos.ApplyRoleResponse> apply(@Valid @RequestBody UserDtos.ApplyRoleRequest request) {
        return Result.ok(userService.applyRole(AuthContext.require().id(), request));
    }

    @GetMapping("/me/applications")
    public Result<List<UserDtos.ApplicationView>> myApplications() {
        return Result.ok(userService.myApplications(AuthContext.require().id()));
    }

    /**
     * 账号禁用申诉：公开接口（被禁用的账号登不进来，只能凭学号申诉）。
     * 路径挂在 /users/me 下是契约要求，鉴权白名单在 WebConfig 里按精确路径放行。
     */
    @PostMapping("/me/appeals")
    public Result<AppealDtos.AppealResponse> appeal(@Valid @RequestBody AppealDtos.AppealRequest request,
                                                    jakarta.servlet.http.HttpServletRequest servletRequest) {
        return Result.ok(appealService.submit(request, clientIp(servletRequest)));
    }

    @GetMapping
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<UserDtos.UserPage> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.listUsers(status, role, dept, keyword, new PageQuery(page, size)));
    }

    @PutMapping("/{id}/status")
    @RequireRoles({"SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<Void> updateStatus(@PathVariable long id,
                                     @Valid @RequestBody UserDtos.StatusUpdateRequest request) {
        userService.updateStatus(id, request);
        return Result.ok();
    }

    @GetMapping("/applications")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<List<UserDtos.ApplicationView>> pendingApplications() {
        return Result.ok(userService.pendingApplications());
    }

    @PutMapping("/applications/{id}")
    @RequireRoles({"ANIMAL_ADMIN", "SYS_ADMIN", "SYSTEM_ADMIN"})
    public Result<UserDtos.ApplicationView> review(@PathVariable long id,
                                                   @Valid @RequestBody UserDtos.ReviewRequest request) {
        boolean approve = "APPROVE".equalsIgnoreCase(request.decision().trim());
        return Result.ok(userService.reviewApplication(id, approve, request.reason()));
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
