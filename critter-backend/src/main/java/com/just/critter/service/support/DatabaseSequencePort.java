package com.just.critter.service.support;

import com.just.critter.domain.archive.SequencePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * seq_no 行锁发号：先原子 upsert 自增，再在同一事务连接内读取当前值。
 * 不能依赖 LAST_INSERT_ID()：连接池下取号与读值可能落在不同连接，读到别的会话的旧值。
 * 调用方必须已在事务中，保证 upsert 与业务写入同生共死。
 */
@Service
public class DatabaseSequencePort implements SequencePort {

    private final JdbcTemplate jdbc;

    public DatabaseSequencePort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int next(String bizType, int year) {
        jdbc.update("""
                INSERT INTO seq_no (biz_type, seq_year, current_no)
                VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE current_no = current_no + 1
                """, bizType, year);
        Integer current = jdbc.queryForObject("""
                SELECT current_no FROM seq_no WHERE biz_type = ? AND seq_year = ?
                """, Integer.class, bizType, year);
        return current == null ? 0 : current;
    }
}
