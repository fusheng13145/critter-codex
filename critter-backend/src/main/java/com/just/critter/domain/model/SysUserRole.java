package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 叠加身份（认养人、志愿者）。主角色在 sys_user.primary_role，这里只放附加角色，
 * 因此"认养人可以同时是学生"不需要拆两套账号。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String roleCode;

    /** PENDING / GRANTED / REJECTED：申请与授予共用一张表，granted_at 为空即未生效 */
    private String status;

    private String applyReason;

    private String availableSlots;

    private Long grantedBy;

    private LocalDateTime grantedAt;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
