package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号禁用申诉（V7 新建，04 数据库设计说明书未定义，按最小可用原则补）。
 *
 * <p>状态：PENDING 待处理 / ACCEPTED 申诉通过 / REJECTED 驳回。
 * 同一学号只允许一条 PENDING，重复提交按幂等处理（返回原记录）。</p>
 */
@Data
@TableName("sys_appeal")
public class SysAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userNo;

    private String reason;

    private String contact;

    private String status;

    private String ip;

    private Long handledBy;

    private LocalDateTime handledAt;

    private String handleNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
