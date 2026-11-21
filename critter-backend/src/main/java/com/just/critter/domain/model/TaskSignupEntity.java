package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("task_signup")
public class TaskSignupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private LocalDateTime signedAt;
    private LocalDateTime checkinAt;
    private String checkinPhoto;
    private BigDecimal hours;
    private Boolean absent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
