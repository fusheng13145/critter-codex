package com.just.critter.service.support;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.mapper.SequenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * seq_no 行锁发号：先确保 (类型, 年份) 行存在，再锁行读当前值、加一、写回。
 * 调用方必须已在事务中，方法用 REQUIRED 加入同一事务。
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
        mapper.ensureRow(bizType, year);
        Integer current = mapper.lockCurrent(bizType, year);
        int next = (current == null ? 0 : current) + 1;
        mapper.updateCurrent(bizType, year, next);
        return next;
    }
}
