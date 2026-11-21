package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.BaseSpecies;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物种名录单表 CRUD；一期只提供只读查询。
 */
@Mapper
public interface BaseSpeciesMapper extends BaseMapper<BaseSpecies> {
}
