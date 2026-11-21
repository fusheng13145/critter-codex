package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动物状态迁移日志，只增不改。
 */
@Mapper
public interface AnimalStatusLogMapper extends BaseMapper<AnimalStatusLog> {
}
