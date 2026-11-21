package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 群体单表 CRUD。selectByCode 用条件构造器走 MP 结果映射，保证 point_ids JSON 正确反序列化。
 */
@Mapper
public interface AnimalGroupMapper extends BaseMapper<AnimalGroup> {

    default AnimalGroup selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<AnimalGroup>()
                .eq(AnimalGroup::getGroupCode, code)
                .last("LIMIT 1"));
    }
}
