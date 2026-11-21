package com.just.critter.job.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.domain.enums.HealthType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.HealthRecord;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.HealthRecordMapper;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 疫苗到期前三天提醒。每天扫描一次 VACCINE 记录，把 next_date 在 [今天, 今天+3]
 * 的记录投递到录入人的站内消息；message.biz_id 用健康记录 id 做幂等去重。
 *
 * <p>这是 T1.4 从"只回 reminderDue 角标"补到"真正发消息"的收尾。</p>
 */
@Component
public class HealthReminderJob {

    private static final Logger log = LoggerFactory.getLogger(HealthReminderJob.class);
    private static final int REMIND_DAYS = 3;
    private static final int LIMIT = 500;

    private final HealthRecordMapper healthRecords;
    private final AnimalMapper animals;
    private final MessageRepository messages;
    private final Clock clock;

    public HealthReminderJob(HealthRecordMapper healthRecords, AnimalMapper animals,
                             MessageRepository messages, Clock clock) {
        this.healthRecords = healthRecords;
        this.animals = animals;
        this.messages = messages;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${critter.jobs.health-reminder-initial-delay-ms:60000}",
            fixedDelayString = "${critter.jobs.health-reminder-interval-ms:86400000}")
    @Transactional
    public HealthReminderResult scan() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<HealthRecord> candidates = healthRecords.selectList(new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getType, HealthType.VACCINE)
                .ge(HealthRecord::getNextDate, today)
                .le(HealthRecord::getNextDate, today.plusDays(REMIND_DAYS))
                .orderByAsc(HealthRecord::getNextDate)
                .last("LIMIT " + LIMIT));

        int sent = 0;
        int skipped = 0;
        for (HealthRecord record : candidates) {
            Long receiverId = record.getOperatorId();
            if (receiverId == null) {
                skipped++;
                continue;
            }
            String bizId = String.valueOf(record.getId());
            if (messages.exists("HEALTH_REMINDER", bizId, receiverId)) {
                skipped++;
                continue;
            }
            Animal animal = record.getAnimalId() == null ? null : animals.selectById(record.getAnimalId());
            if (animal == null) {
                skipped++;
                continue;
            }
            String title = "疫苗提醒：" + animal.getCode() + " 的" + healthTypeLabel(record.getType())
                    + "将于 " + record.getNextDate() + " 到期";
            if (title.length() > 100) {
                title = title.substring(0, 99) + "…";
            }
            int rows = messages.insert(new MessageWrite(receiverId, "HEALTH_REMINDER", title,
                    "HEALTH_RECORD", bizId, null, now));
            if (rows > 0) {
                sent++;
            } else {
                skipped++;
            }
        }
        log.info("疫苗提醒扫描完成 candidates={} sent={} skipped={}", candidates.size(), sent, skipped);
        return new HealthReminderResult(candidates.size(), sent, skipped);
    }

    private String healthTypeLabel(HealthType type) {
        return type == null ? "疫苗" : type.label();
    }
}
