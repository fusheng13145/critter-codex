package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 救助工单单表 CRUD；状态流转由服务层调状态机。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
