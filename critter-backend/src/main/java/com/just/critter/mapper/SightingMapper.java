package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Sighting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 群体目击单表 CRUD；归属判定后续迭代补。
 */
@Mapper
public interface SightingMapper extends BaseMapper<Sighting> {
}
