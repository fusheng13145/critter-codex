package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * base_clinic 表映射。仅用于新增合作医院并回填自增主键。
 */
@Data
@TableName("base_clinic")
public class ClinicEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String clinicName;
    private String contact;
    private String phone;
    private String address;
    private String priceNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
