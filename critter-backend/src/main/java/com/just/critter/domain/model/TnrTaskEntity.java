package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * tnr_task 表映射：一行代表一个任务，stage 保存当前节点。
 */
@Data
@TableName("tnr_task")
public class TnrTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tnrNo;
    private Long animalId;
    private String stage;
    private Long handlerId;
    private LocalDateTime occurredAt;
    private String fileIds;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
