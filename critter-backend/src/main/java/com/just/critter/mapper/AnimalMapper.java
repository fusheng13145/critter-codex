package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.Animal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 个体档案单表 CRUD。列表筛选走 MyBatis-Plus 条件构造器，避免拼 SQL 字符串。
 */
@Mapper
public interface AnimalMapper extends BaseMapper<Animal> {

    @Select("SELECT * FROM animal WHERE code = #{code} AND deleted = 0 LIMIT 1")
    Animal selectByCode(@Param("code") String code);
}
