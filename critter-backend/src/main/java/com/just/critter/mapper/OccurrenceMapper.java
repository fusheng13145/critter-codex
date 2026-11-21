package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Occurrence;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物种出现记录单表 CRUD；季节筛选在服务层按枚举月份下推 SQL。
 */
@Mapper
public interface OccurrenceMapper extends BaseMapper<Occurrence> {
}
