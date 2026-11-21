package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.HealthRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康记录单表 CRUD；查询按动物过滤，提醒由服务层按日期计算。
 */
@Mapper
public interface HealthRecordMapper extends BaseMapper<HealthRecord> {
}
