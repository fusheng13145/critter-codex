package com.just.critter.mapper;

import com.just.critter.domain.model.Animal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdoptionAnimalMapper {

    @Select("""
            SELECT id, code, status
            FROM animal
            WHERE code = #{code} AND deleted = 0
            FOR UPDATE
            """)
    Animal lockByCode(@Param("code") String code);
}
