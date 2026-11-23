package com.just.critter.job.identity;

import com.just.critter.service.user.RoleExpiryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 每小时第 40 分（Asia/Shanghai）回收过期临时授权。
 * 业务规则在 {@link RoleExpiryService}，任务只负责触发；
 * 频率不用太密：rolesOf 在读取侧已经过滤过期行，晚几分钟回收不影响权限正确性。
 */
@Component
public class RoleExpiryJob {

    private final RoleExpiryService roleExpiryService;
    private final Clock clock;

    public RoleExpiryJob(RoleExpiryService roleExpiryService, Clock clock) {
        this.roleExpiryService = roleExpiryService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 40 * * * *", zone = "Asia/Shanghai")
    public void run() {
        roleExpiryService.reclaim();
    }
}
