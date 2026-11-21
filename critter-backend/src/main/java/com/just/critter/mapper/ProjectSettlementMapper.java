package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.ProjectSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 结项公示单表 CRUD；project_id 唯一，发布后不允许修改。
 */
@Mapper
public interface ProjectSettlementMapper extends BaseMapper<ProjectSettlement> {

    @Select("SELECT * FROM project_settlement WHERE project_id = #{projectId} LIMIT 1")
    ProjectSettlement selectByProjectId(@Param("projectId") Long projectId);
}
