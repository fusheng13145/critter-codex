package com.just.critter.domain.archive;

/**
 * 业务编号发号端口。数据库实现用 seq_no 表 + 行锁取号；测试用内存实现。
 * 同一 (bizType, year) 下必须原子递增，保证并发不重号。
 */
public interface SequencePort {

    int next(String bizType, int year);
}
