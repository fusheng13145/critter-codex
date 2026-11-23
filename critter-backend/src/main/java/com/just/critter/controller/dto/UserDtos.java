package com.just.critter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 用户域出入参：个人中心、身份申请、登录日志、用户列表。 */
public final class UserDtos {

    private UserDtos() {
    }

    /** /users/me：本人可见完整资料；phone 由服务层按"是否本人"决定全量或脱敏。 */
    public record ProfileResponse(
            long id,
            String userNo,
            String email,
            String nickname,
            String dept,
            String phone,
            String phoneMasked,
            String primaryRole,
            String primaryRoleLabel,
            List<String> roles,
            List<MenuView> menus,
            String status,
            LocalDateTime createdAt) {
    }

    public record MenuView(String code, String title, String path, String owner) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "昵称不能为空")
            @Size(min = 2, max = 16, message = "昵称长度需在 2—16 字之间") String nickname,
            @Size(max = 64, message = "院系名称过长") String dept,
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 8, max = 32, message = "新密码长度需在 8—32 位之间") String newPassword) {
    }

    public record ApplyRoleRequest(
            @NotBlank(message = "请选择申请身份") String roleCode,
            @NotBlank(message = "请填写申请理由")
            @Size(min = 10, max = 255, message = "申请理由不少于 10 字") String reason,
            String availableSlots) {
    }

    public record ApplyRoleResponse(long applicationId, String roleCode, String status) {
    }

    public record ApplicationView(
            long id,
            String roleCode,
            String roleLabel,
            String status,
            String reason,
            LocalDateTime appliedAt,
            Long grantedBy,
            /** 临时授权的到期时间；null 表示永久。过期后由 RoleExpiryJob 回收，rolesOf 即时过滤 */
            LocalDateTime expireAt) {
    }

    public record LoginLogView(Long id, String action, String result, String ip,
                               String requestId, LocalDateTime createdAt) {
    }

    public record UserListItem(
            long id,
            String userNo,
            String nickname,
            String dept,
            String email,
            String phoneMasked,
            String primaryRole,
            List<String> roles,
            String status,
            LocalDateTime createdAt) {
    }

    public record UserPage(List<UserListItem> list, PageMeta page) {
    }

    public record PageMeta(int page, int size, long total, long pages) {
    }

    public record StatusUpdateRequest(
            @NotBlank(message = "请选择目标状态") String status,
            @Size(max = 255, message = "原因过长") String reason) {
    }

    public record ReviewRequest(
            @NotBlank(message = "请选择审核结果") String decision,
            @Size(max = 255, message = "审核意见过长") String reason,
            /** 可选：临时授权小时数（1—720）。不传即永久授予，到期由定时任务回收 */
            Integer expireHours) {
    }
}
