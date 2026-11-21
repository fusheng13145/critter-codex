package com.just.critter.domain.archive;

import java.time.Clock;
import java.time.Year;
import java.util.Objects;

/**
 * 群体编号：GRP-年份-流水，流水在 GROUP 维度独立递增。
 */
public class GroupCodeGenerator {

    private static final String BIZ_TYPE = "GROUP";

    private final SequencePort sequences;
    private final Clock clock;

    public GroupCodeGenerator(SequencePort sequences, Clock clock) {
        this.sequences = Objects.requireNonNull(sequences);
        this.clock = Objects.requireNonNull(clock);
    }

    public String next() {
        int year = Year.now(clock).getValue();
        int sequence = sequences.next(BIZ_TYPE, year);
        return String.format("GRP-%d-%03d", year, sequence);
    }
}
