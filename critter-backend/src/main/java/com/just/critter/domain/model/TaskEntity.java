package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class TaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String title;
    private String type;
    private String description;
    private Long pointId;
    private LocalDateTime startAt;
    private Integer quota;
    private BigDecimal durationHours;
    private Integer joined;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
