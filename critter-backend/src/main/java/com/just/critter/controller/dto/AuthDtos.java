package com.just.critter.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 身份域出入参（接口设计 §7.1 与成员任务 §1.2 第 1—5 项）。字段名与契约一致，只增不改。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入学号") String account,
            @NotBlank(message = "请输入校园邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请输入密码") String password,
            @NotBlank(message = "请输入昵称") String nickname,
            String dept,
            String phone) {
    }

    public record RegisterResponse(String userNo, String status, String email,
                                   Integer codeTtlSeconds, String devCode) {
    }

    public record VerifyRequest(
            @NotBlank(message = "请输入邮箱") String email,
            @NotBlank(message = "请输入验证码") String code) {
    }

    public record VerifyResponse(String userNo, String status) {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入学号或校园邮箱") String account,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record LoginUser(
            long id,
            String userNo,
            String nickname,
            String dept,
            String primaryRole,
            String primaryRoleLabel,
            List<String> roles,
            List<String> menus) {
    }

    public record LoginResponse(String accessToken, String refreshToken, long expiresIn, LoginUser user) {
    }

    public record TokenResponse(String accessToken, long expiresIn) {
    }

    public record PhoneSensitiveView(String phone, String phoneMasked, boolean visible) {
    }

    // ---------------- 找回密码（接口设计 §6.1 第 6—7 项） ----------------

    /** 发验证码：账号可以是学号或校园邮箱，与登录口径一致。 */
    public record ResetCodeRequest(
            @NotBlank(message = "请输入学号或校园邮箱") String account) {
    }

    /** 验证码只回传到脱敏邮箱；演示环境额外回传 devCode，生产置 expose-verification-code=false。 */
    public record ResetCodeResponse(String userNo, String emailMasked, Integer codeTtlSeconds, String devCode) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "请输入学号或校园邮箱") String account,
            @NotBlank(message = "请输入验证码") String code,
            @NotBlank(message = "请输入新密码") String newPassword) {
    }
}
