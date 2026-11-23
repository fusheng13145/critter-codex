package com.just.critter.care.plan;

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

@Component
public class CarePlanReminderJob {

    private static final Logger log = LoggerFactory.getLogger(CarePlanReminderJob.class);
    private static final int REMIND_DAYS = 3;
    private static final int LIMIT = 500;

    private final CarePlanRepository plans;
    private final MessageRepository messages;
    private final Clock clock;

    public CarePlanReminderJob(CarePlanRepository plans, MessageRepository messages, Clock clock) {
        this.plans = plans;
        this.messages = messages;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${critter.jobs.care-plan-reminder-initial-delay-ms:90000}",
            fixedDelayString = "${critter.jobs.care-plan-reminder-interval-ms:86400000}")
    @Transactional
    public CarePlanReminderResult scan() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        List<CarePlanRepository.CarePlanDue> due = plans.findDueForReminder(
                today, today.plusDays(REMIND_DAYS), LIMIT);
        int sent = 0;
        int skipped = 0;
        for (CarePlanRepository.CarePlanDue plan : due) {
            List<Long> receivers = plans.findActiveAdopter(plan.animalId());
            if (receivers.isEmpty()) {
                receivers = plans.findAnimalAdmins();
            }
            if (receivers.isEmpty()) {
                skipped++;
                continue;
            }
            String typeLabel = "VACCINE".equals(plan.type()) ? "疫苗" : "用药";
            String title = typeLabel + "计划提醒：" + plan.animalCode() + " 的" + plan.itemName()
                    + " 将于 " + plan.planDate() + " 到期";
            if (title.length() > 128) {
                title = title.substring(0, 127) + "…";
            }
            boolean any = false;
            for (Long receiverId : receivers) {
                if (messages.exists("CARE_REMINDER", String.valueOf(plan.id()), receiverId)) {
                    skipped++;
                    continue;
                }
                int rows = messages.insert(new MessageWrite(receiverId, "CARE_REMINDER", title,
                        "CARE_PLAN", String.valueOf(plan.id()), null, now));
                if (rows > 0) {
                    sent++;
                    any = true;
                } else {
                    skipped++;
                }
            }
            if (any) {
                plans.markReminded(plan.id(), today);
            }
        }
        log.info("疫苗用药计划提醒完成 candidates={} sent={} skipped={}", due.size(), sent, skipped);
        return new CarePlanReminderResult(due.size(), sent, skipped);
    }

    public record CarePlanReminderResult(int candidates, int sent, int skipped) {
    }
}
