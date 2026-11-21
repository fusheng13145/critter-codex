package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件元信息：业务表只存 fileId，实体文件落盘在 storage-dir。
 * auditStatus：ANIMAL_PUBLIC / PROJECT_COVER 需审核（PENDING→PASSED），其余业务类型直接 PASSED。
 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileId;

    private String bizType;

    private String bizId;

    private String name;

    private String path;

    private String mime;

    private Long size;

    private Long uploaderId;

    /** PENDING / PASSED / REJECTED */
    private String auditStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;
}
