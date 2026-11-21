package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * visit 表映射。file_ids 以 JSON 文本写入 MySQL JSON 列。
 */
@Data
@TableName("visit")
public class VisitEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String visitNo;
    private Long animalId;
    private Long orderId;
    private Long clinicId;
    private LocalDateTime visitedAt;
    private String complaint;
    private String diagnosis;
    private String treatment;
    private Integer cost;
    private String fileIds;
    private String sourceNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
