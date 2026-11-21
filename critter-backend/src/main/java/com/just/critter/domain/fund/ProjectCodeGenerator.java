package com.just.critter.domain.fund;

import com.just.critter.domain.archive.SequencePort;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * 项目编号：PRJ-年份-流水（至少三位）。
 * 流水按年在 seq_no 中单独建行，跨年重新计数；格式与 04 §3.4 示例一致。
 */
public class ProjectCodeGenerator {

    private static final String BIZ_TYPE_PREFIX = "PROJECT_";

    private final SequencePort sequences;
    private final Clock clock;

    public ProjectCodeGenerator(SequencePort sequences, Clock clock) {
        this.sequences = Objects.requireNonNull(sequences);
        this.clock = Objects.requireNonNull(clock);
    }

    public String next() {
        int year = LocalDate.now(clock).getYear();
        int sequence = sequences.next(BIZ_TYPE_PREFIX + year, year);
        return String.format(Locale.ROOT, "PRJ-%d-%03d", year, sequence);
    }
}
