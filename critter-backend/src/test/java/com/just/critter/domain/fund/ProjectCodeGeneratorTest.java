package com.just.critter.domain.fund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.4 验收：项目编号 PRJ-年份-流水，流水按年在 seq_no 中单独建行。
 */
class ProjectCodeGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Test
    @DisplayName("项目编号：PRJ-2026-007，流水至少三位")
    void next_formats_project_no() {
        ProjectCodeGenerator generator = new ProjectCodeGenerator((bizType, year) -> {
            assertThat(bizType).isEqualTo("PROJECT_2026");
            assertThat(year).isEqualTo(2026);
            return 7;
        }, CLOCK);

        assertThat(generator.next()).isEqualTo("PRJ-2026-007");
    }

    @Test
    @DisplayName("项目编号：跨年使用新的 seq_no 键")
    void next_uses_year_key() {
        ProjectCodeGenerator generator = new ProjectCodeGenerator((bizType, year) -> {
            assertThat(bizType).isEqualTo("PROJECT_2027");
            assertThat(year).isEqualTo(2027);
            return 1;
        }, Clock.fixed(Instant.parse("2027-01-01T01:00:00Z"), ZONE));

        assertThat(generator.next()).isEqualTo("PRJ-2027-001");
    }
}
