package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AdoptionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdoptionMapper extends BaseMapper<AdoptionEntity> {

    @Select("""
            SELECT adoption_no
            FROM adoption
            WHERE animal_id = #{animalId} AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """)
    String findActiveAdoptionNo(@Param("animalId") long animalId);

    @Select("""
            SELECT COUNT(*)
            FROM adoption
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    long countActiveByUser(@Param("userId") long userId);
}
