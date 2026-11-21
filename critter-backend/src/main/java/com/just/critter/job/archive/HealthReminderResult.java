package com.just.critter.job.archive;

/**
 * 疫苗提醒扫描结果。
 */
public record HealthReminderResult(int candidates, int sent, int skipped) {
}
