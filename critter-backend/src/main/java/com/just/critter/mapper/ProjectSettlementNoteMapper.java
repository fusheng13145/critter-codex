package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.ProjectSettlementNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结项公示追加说明单表 CRUD。
 */
@Mapper
public interface ProjectSettlementNoteMapper extends BaseMapper<ProjectSettlementNote> {
}
