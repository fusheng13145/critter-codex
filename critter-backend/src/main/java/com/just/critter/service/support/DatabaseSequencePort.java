package com.just.critter.service.support;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.mapper.SequenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * seq_no 行锁发号：用单条 INSERT ... ON DUPLICATE KEY UPDATE 完成“建行 + 自增 + 回写”，
 * 由 InnoDB 行锁串行化同一 (类型, 年份) 的并发取号，调用方必须已在事务中。
 */
@Service
public class DatabaseSequencePort implements SequencePort {

    private final SequenceMapper mapper;

    public DatabaseSequencePort(SequenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int next(String bizType, int year) {
        mapper.nextValue(bizType, year);
        return mapper.currentValue(bizType, year);
    }
}
