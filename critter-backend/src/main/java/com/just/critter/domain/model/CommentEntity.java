package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class CommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizId;
    private Long authorId;
    private String content;
    private String status;
    private String replyContent;
    private Boolean replyVisible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
