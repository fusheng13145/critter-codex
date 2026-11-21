package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * adoption 表映射。active_flag 是数据库生成列，不在此映射，避免被 MyBatis 写入。
 */
@Data
@TableName("adoption")
public class AdoptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String adoptionNo;
    private Long userId;
    private Long animalId;
    private String animalCode;
    private Long planId;
    private Integer amount;
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long auditorId;
    private String auditReason;
    private String certificateNo;
    private LocalDateTime payDeadline;
    private String terminationReason;
    private String contact;
    private String remark;
    private LocalDateTime promiseAcceptedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
