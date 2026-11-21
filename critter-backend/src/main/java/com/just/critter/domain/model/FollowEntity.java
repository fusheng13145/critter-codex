package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class FollowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String bizType;
    private String bizId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
