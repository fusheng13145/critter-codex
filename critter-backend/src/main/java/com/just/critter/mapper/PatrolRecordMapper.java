package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.PatrolRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PatrolRecordMapper extends BaseMapper<PatrolRecord> {
}
