package com.just.critter.domain.fund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3.1 验收：捐赠编号 DN + yyyyMMdd + 当日流水（至少四位）。
 */
class DonationCodeGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Test
    @DisplayName("捐赠编号：DN202611210031")
    void next_formats_donation_no() {
        DonationCodeGenerator generator = new DonationCodeGenerator((bizType, year) -> {
            assertThat(bizType).isEqualTo("DONATION_20261121");
            assertThat(year).isEqualTo(2026);
            return 31;
        }, CLOCK);

        assertThat(generator.next()).isEqualTo("DN202611210031");
    }

    @Test
    @DisplayName("捐赠编号：跨日使用新的 seq_no 键")
    void next_uses_day_key() {
        DonationCodeGenerator generator = new DonationCodeGenerator((bizType, year) -> {
            assertThat(bizType).isEqualTo("DONATION_20261122");
            assertThat(year).isEqualTo(2026);
            return 1;
        }, Clock.fixed(Instant.parse("2026-11-21T16:01:00Z"), ZONE));

        assertThat(generator.next()).isEqualTo("DN202611220001");
    }
}
