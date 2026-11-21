package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号与身份（数据库设计 §3.11）。phone 存 AES 密文，序列化层按角色脱敏。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userNo;

    private String email;

    private String passwordHash;

    private String nickname;

    private String dept;

    /** AES/GCM 密文，禁止直接回前端 */
    private String phone;

    private String primaryRole;

    /** PENDING / ACTIVE / LOCKED / DISABLED */
    private String status;

    private Integer failCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime appliedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;
}
