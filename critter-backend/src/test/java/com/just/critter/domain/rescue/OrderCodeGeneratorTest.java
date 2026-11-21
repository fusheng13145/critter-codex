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
 * T2.2 验收：工单编号 RO + 日期 + 当日流水，跨日重新计数。
 */
class OrderCodeGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, Integer> counters = new HashMap<>();
    private final SequencePort sequences = (bizType, year) ->
            counters.merge(bizType + ":" + year, 1, Integer::sum);

    @Test
    @DisplayName("首条工单编号为 RO20261121001")
    void first_order_no() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        OrderCodeGenerator generator = new OrderCodeGenerator(sequences, clock);

        assertThat(generator.next()).isEqualTo("RO20261121001");
    }

    @Test
    @DisplayName("同一天流水连续递增")
    void sequence_keeps_incrementing() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        OrderCodeGenerator generator = new OrderCodeGenerator(sequences, clock);

        assertThat(generator.next()).isEqualTo("RO20261121001");
        assertThat(generator.next()).isEqualTo("RO20261121002");
    }

    @Test
    @DisplayName("跨日重新计数")
    void next_day_resets() {
        Clock dayOne = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);
        Clock dayTwo = Clock.fixed(Instant.parse("2026-11-22T02:31:00Z"), ZONE);
        OrderCodeGenerator first = new OrderCodeGenerator(sequences, dayOne);
        OrderCodeGenerator second = new OrderCodeGenerator(sequences, dayTwo);

        assertThat(first.next()).isEqualTo("RO20261121001");
        assertThat(second.next()).isEqualTo("RO20261122001");
    }
}
