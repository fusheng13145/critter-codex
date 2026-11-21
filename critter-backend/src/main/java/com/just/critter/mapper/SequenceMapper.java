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
     * 原子取号：首次插入用 LAST_INSERT_ID(1) 初始化会话值，
     * 已存在则在同一行锁内自增并回写 LAST_INSERT_ID。避免“先 INSERT IGNORE 再 SELECT FOR UPDATE”
     * 在并发首取时产生插入意向锁与行锁交叉死锁。
     */
    @Insert("""
            INSERT INTO seq_no (biz_type, seq_year, current_no)
            VALUES (#{bizType}, #{year}, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE current_no = LAST_INSERT_ID(current_no + 1)
            """)
    int nextValue(@Param("bizType") String bizType, @Param("year") int year);

    @Select("SELECT LAST_INSERT_ID()")
    int lastInsertId();
}
