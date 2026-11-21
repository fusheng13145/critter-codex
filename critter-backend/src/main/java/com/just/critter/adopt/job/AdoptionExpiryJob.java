package com.just.critter.adopt.job;

import com.just.critter.adopt.service.AdoptionExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 每日 02:00（Asia/Shanghai）执行 BR-05；业务规则都在 Service，任务只负责触发。
 */
@Component
public class AdoptionExpiryJob {

    private final AdoptionExpiryService expiryService;
    private final Clock clock;

    public AdoptionExpiryJob(AdoptionExpiryService expiryService, Clock clock) {
        this.expiryService = expiryService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai")
    public void run() {
        expiryService.process(LocalDate.now(clock));
    }
}
