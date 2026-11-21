package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上报单单表 CRUD；24 小时去重在服务层用条件构造器查询。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
