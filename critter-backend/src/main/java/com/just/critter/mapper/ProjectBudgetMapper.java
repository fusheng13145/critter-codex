package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.ProjectBudget;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目预算明细单表 CRUD。
 */
@Mapper
public interface ProjectBudgetMapper extends BaseMapper<ProjectBudget> {
}
