package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段级变更日志，只增不改。
 */
@Mapper
public interface AnimalChangeLogMapper extends BaseMapper<AnimalChangeLog> {
}
