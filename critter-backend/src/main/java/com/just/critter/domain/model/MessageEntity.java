package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class MessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private String type;
    private String title;
    private String bizType;
    private String bizId;
    private Boolean readFlag;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
