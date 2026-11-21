package com.just.critter.domain.archive;

import java.time.Clock;
import java.time.Year;
import java.util.Objects;

/**
 * 编号生成：前缀取类别，年份取时钟，流水由发号端口给。格式"类别-年份-流水号"，
 * 例如 CAT-2026-031；流水超过三位自然增宽，不截断。
 */
public class AnimalCodeGenerator {

    private static final String BIZ_TYPE_PREFIX = "ANIMAL_";

    private final SequencePort sequences;
    private final Clock clock;

    public AnimalCodeGenerator(SequencePort sequences, Clock clock) {
        this.sequences = Objects.requireNonNull(sequences);
        this.clock = Objects.requireNonNull(clock);
    }

    public String next(AnimalCategory category) {
        int year = Year.now(clock).getValue();
        int sequence = sequences.next(BIZ_TYPE_PREFIX + category.name(), year);
        return String.format("%s-%d-%03d", category.name(), year, sequence);
    }
}
