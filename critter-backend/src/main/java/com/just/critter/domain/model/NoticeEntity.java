package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice")
public class NoticeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeNo;
    private String category;
    private String title;
    private String content;
    private Long authorId;
    private Boolean pinned;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
