package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（FR-SYS-02）：关键写操作与登录事件都落这里，
 * before/after 存 JSON 字符串，台账与档案对不上时可逐条追问。
 */
@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorId;

    private String actorRole;

    /** 动作码，如 LOGIN / REGISTER / PASSWORD_CHANGE / DICT_CREATE */
    private String action;

    private String bizType;

    private String bizId;

    private String beforeJson;

    private String afterJson;

    private String ip;

    /** SUCCESS / FAIL */
    private String result;

    private Integer failCode;

    private String requestId;

    private LocalDateTime createdAt;
}
