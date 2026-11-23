package com.just.critter.adopt.benefit;

import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动物离世时，把生效认养关系自动终止，并给认养人投递站内消息。
 * 终止后专属页鉴权立即返回 403，不需要等定时任务。
 */
@Service
public class AnimalDeathService {

    private final JdbcTemplate jdbc;
    private final MessageRepository messages;

    public AnimalDeathService(JdbcTemplate jdbc, MessageRepository messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    @Transactional
    public int onDeath(long animalId, String animalCode, long operatorId, LocalDateTime now) {
        List<ActiveAdoption> actives = jdbc.query("""
                SELECT id, adoption_no, user_id
                FROM adoption
                WHERE animal_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> new ActiveAdoption(rs.getLong("id"),
                rs.getString("adoption_no"), rs.getLong("user_id")), animalId);
        int notified = 0;
        for (ActiveAdoption active : actives) {
            int rows = jdbc.update("""
                    UPDATE adoption
                    SET status = 'TERMINATED',
                        audit_reason = '动物已离世，认养关系自动终止'
                    WHERE id = ? AND status = 'ACTIVE'
                    """, active.id());
            if (rows == 0) {
                continue;
            }
            String title = "认养动物离世通知：" + animalCode;
            if (title.length() > 128) {
                title = title.substring(0, 127) + "…";
            }
            messages.insert(new MessageWrite(active.userId(), "ANIMAL_DEATH", title,
                    "ADOPTION", active.adoptionNo(), operatorId, now));
            notified++;
        }
        return notified;
    }

    private record ActiveAdoption(long id, String adoptionNo, long userId) {
    }
}
