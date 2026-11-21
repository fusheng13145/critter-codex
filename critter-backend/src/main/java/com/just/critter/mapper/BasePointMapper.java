package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.BasePoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点位只读查询；点位维护接口归 FR-A-06，暂不在本轮实现。
 */
@Mapper
public interface BasePointMapper extends BaseMapper<BasePoint> {
}
