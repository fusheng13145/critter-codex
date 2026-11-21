package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 募捐项目单表 CRUD。列表筛选走 MyBatis-Plus 条件构造器。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    @Select("SELECT * FROM project WHERE project_no = #{projectNo} AND deleted = 0 LIMIT 1")
    Project selectByProjectNo(@Param("projectNo") String projectNo);

    /**
     * 核对入账时锁项目行，保证 raised_amount 只由已入账捐赠递增；
     * 与 T3.2 支出登记共用同一把项目行锁。
     */
    @Select("SELECT * FROM project WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Project selectByIdForUpdate(@Param("id") Long id);
}
