package com.just.critter.domain.rescue;

import com.just.critter.domain.archive.SequencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.1 验收：上报编号 RP + 日期 + 当日流水，跨日重新计数。
 */
class ReportCodeGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, Integer> counters = new HashMap<>();
    private final SequencePort sequences = (bizType, year) ->
            counters.merge(bizType + ":" + year, 1, Integer::sum);

    @Test
    @DisplayName("首条上报编号为 RP20261121001")
    void first_report_no() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        ReportCodeGenerator generator = new ReportCodeGenerator(sequences, clock);

        assertThat(generator.next()).isEqualTo("RP20261121001");
    }

    @Test
    @DisplayName("同一天流水连续递增，不跳号")
    void sequence_keeps_incrementing() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        ReportCodeGenerator generator = new ReportCodeGenerator(sequences, clock);

        assertThat(generator.next()).isEqualTo("RP20261121001");
        assertThat(generator.next()).isEqualTo("RP20261121002");
        assertThat(generator.next()).isEqualTo("RP20261121003");
    }

    @Test
    @DisplayName("跨日重新计数")
    void next_day_resets() {
        Clock dayOne = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        Clock dayTwo = Clock.fixed(Instant.parse("2026-11-22T02:31:00Z"), ZONE);
        ReportCodeGenerator first = new ReportCodeGenerator(sequences, dayOne);
        ReportCodeGenerator second = new ReportCodeGenerator(sequences, dayTwo);

        assertThat(first.next()).isEqualTo("RP20261121001");
        assertThat(second.next()).isEqualTo("RP20261122001");
    }
}
