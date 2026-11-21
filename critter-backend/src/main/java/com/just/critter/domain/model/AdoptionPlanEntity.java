package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * adoption_plan 表映射。金额单位分。
 */
@Data
@TableName("adoption_plan")
public class AdoptionPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planName;
    private String period;
    private Integer amount;
    private String benefits;
    private Boolean enabled;
}
