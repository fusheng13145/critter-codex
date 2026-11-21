package com.just.critter.domain.fund;

import com.just.critter.domain.archive.SequencePort;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 支出编号：EX + yyyyMMdd + 当日流水（至少四位），与捐赠编号同一套序列端口。
 */
public class ExpenseCodeGenerator {

    private static final String BIZ_TYPE_PREFIX = "EXPENSE_";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SequencePort sequences;
    private final Clock clock;

    public ExpenseCodeGenerator(SequencePort sequences, Clock clock) {
        this.sequences = Objects.requireNonNull(sequences);
        this.clock = Objects.requireNonNull(clock);
    }

    public String next() {
        LocalDate today = LocalDate.now(clock);
        String day = DAY.format(today);
        int sequence = sequences.next(BIZ_TYPE_PREFIX + day, today.getYear());
        return "EX" + day + String.format(Locale.ROOT, "%04d", sequence);
    }
}
