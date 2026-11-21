package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * tnr_node 表映射：每个节点一条流水，保留责任人与时间。
 */
@Data
@TableName("tnr_node")
public class TnrNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tnrNo;
    private String stage;
    private Long handlerId;
    private LocalDateTime occurredAt;
    private String fileIds;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
