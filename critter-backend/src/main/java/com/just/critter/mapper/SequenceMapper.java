package com.just.critter.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 发号器行锁操作。必须与业务写入在同一事务内，否则会出现"取号成功、建档失败"的空号。
 */
@Mapper
public interface SequenceMapper {

    @Insert("INSERT IGNORE INTO seq_no (biz_type, seq_year, current_no) VALUES (#{bizType}, #{year}, 0)")
    int ensureRow(@Param("bizType") String bizType, @Param("year") int year);

    @Select("SELECT current_no FROM seq_no WHERE biz_type = #{bizType} AND seq_year = #{year} FOR UPDATE")
    Integer lockCurrent(@Param("bizType") String bizType, @Param("year") int year);

    @Update("UPDATE seq_no SET current_no = #{next} WHERE biz_type = #{bizType} AND seq_year = #{year}")
    int updateCurrent(@Param("bizType") String bizType, @Param("year") int year, @Param("next") int next);
}
