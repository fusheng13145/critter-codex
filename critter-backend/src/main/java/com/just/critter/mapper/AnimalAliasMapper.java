package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 档案副编号别名。只用于把旧编号解析到主档案，不反向修改主档。
 */
@Mapper
public interface AnimalAliasMapper extends BaseMapper<AnimalAlias> {

    @Select("SELECT * FROM animal_alias WHERE alias_code = #{aliasCode} LIMIT 1")
    AnimalAlias selectByAliasCode(@Param("aliasCode") String aliasCode);

    @Select("""
            SELECT animal_id
            FROM animal_alias
            WHERE alias_code LIKE CONCAT('%', #{keyword}, '%')
            LIMIT 200
            """)
    List<Long> selectAnimalIdsByAliasLike(@Param("keyword") String keyword);
}
