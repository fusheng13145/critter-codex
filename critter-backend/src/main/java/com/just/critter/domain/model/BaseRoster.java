package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学籍名单：注册时比对学号，名单外一律 30202。 */
@Data
@TableName("base_roster")
public class BaseRoster {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userNo;

    private String name;

    private String dept;

    private String grade;

    private String importedBatch;

    private LocalDateTime createdAt;
}
