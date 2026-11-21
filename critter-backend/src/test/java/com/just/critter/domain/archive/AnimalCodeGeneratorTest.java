package com.just.critter.domain.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验收标准（功能分析 §4.2 FR-A-01、成员 B 任务书 §1.3）：
 * 编号规则"类别-年份-流水号"，同类别当年流水递增，连续、不跳号、不重复。
 */
class AnimalCodeGeneratorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    /** 内存版发号器，替代尚未定版的数据库实现。 */
    private final Map<String, Integer> counters = new HashMap<>();

    private final SequencePort sequences = (bizType, year) ->
            counters.merge(bizType + ":" + year, 1, Integer::sum);

    private final AnimalCodeGenerator generator = new AnimalCodeGenerator(sequences, clock);

    @Test
    @DisplayName("首只猫的编号为 CAT-2026-001")
    void first_code() {
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-001");
    }

    @Test
    @DisplayName("同类别当年流水连续递增，不跳号")
    void sequence_keeps_incrementing() {
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-001");
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-002");
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-003");
    }

    @Test
    @DisplayName("不同类别各自独立计数")
    void categories_have_own_sequence() {
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-001");
        assertThat(generator.next(AnimalCategory.DOG)).isEqualTo("DOG-2026-001");
        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-002");
    }

    @Test
    @DisplayName("流水超过三位时不截断")
    void sequence_beyond_three_digits() {
        for (int i = 0; i < 999; i++) {
            generator.next(AnimalCategory.CAT);
        }

        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-1000");
    }

    @Test
    @DisplayName("年份取自系统时钟，跨年重新计数")
    void year_comes_from_clock() {
        Clock nextYear = Clock.fixed(Instant.parse("2027-01-05T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        AnimalCodeGenerator generator2027 = new AnimalCodeGenerator(sequences, nextYear);

        assertThat(generator.next(AnimalCategory.CAT)).isEqualTo("CAT-2026-001");
        assertThat(generator2027.next(AnimalCategory.CAT)).isEqualTo("CAT-2027-001");
    }
}
