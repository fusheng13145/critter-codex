package com.just.critter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 账号禁用申诉（接口设计 §6.1 第 14 项，公开接口）。 */
public final class AppealDtos {

    private AppealDtos() {
    }

    public record AppealRequest(
            @NotBlank(message = "请输入学号或工号") String userNo,
            @NotBlank(message = "请填写申诉理由") @Size(min = 5, max = 500, message = "申诉理由需在 5—500 字之间") String reason,
            @Size(max = 64, message = "联系方式过长") String contact) {
    }

    public record AppealResponse(long id, String userNo, String status, String statusLabel,
                                 String message, LocalDateTime createdAt) {
    }
}
