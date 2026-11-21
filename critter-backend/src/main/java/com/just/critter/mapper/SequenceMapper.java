package com.just.critter.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发号器行锁操作。必须与业务写入在同一事务内，否则会出现"取号成功、建档失败"的空号。
 */
@Mapper
public interface SequenceMapper {

    /**
     * 原子取号：首次插入 current_no=1，已存在则在同一行锁内自增。
     * 取号后必须再读 current_no，不能依赖 LAST_INSERT_ID()：首次 INSERT 时
     * LAST_INSERT_ID 会被 seq_no 的自增主键覆盖，返回行号而不是流水号。
     */
    @Insert("""
            INSERT INTO seq_no (biz_type, seq_year, current_no)
            VALUES (#{bizType}, #{year}, 1)
            ON DUPLICATE KEY UPDATE current_no = current_no + 1
            """)
    int nextValue(@Param("bizType") String bizType, @Param("year") int year);

    @Select("""
            SELECT current_no FROM seq_no
            WHERE biz_type = #{bizType} AND seq_year = #{year}
            """)
    int currentValue(@Param("bizType") String bizType, @Param("year") int year);
}
