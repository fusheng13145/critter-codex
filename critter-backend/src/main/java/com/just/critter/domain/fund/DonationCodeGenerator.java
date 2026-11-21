package com.just.critter.domain.fund;

import com.just.critter.domain.archive.SequencePort;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 捐赠编号：DN + yyyyMMdd + 当日流水（至少四位）。
 * 流水按天在 seq_no 中单独建行，跨日重新计数。
 */
public class DonationCodeGenerator {

    private static final String BIZ_TYPE_PREFIX = "DONATION_";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SequencePort sequences;
    private final Clock clock;

    public DonationCodeGenerator(SequencePort sequences, Clock clock) {
        this.sequences = Objects.requireNonNull(sequences);
        this.clock = Objects.requireNonNull(clock);
    }

    public String next() {
        LocalDate today = LocalDate.now(clock);
        String day = DAY.format(today);
        int sequence = sequences.next(BIZ_TYPE_PREFIX + day, today.getYear());
        return "DN" + day + String.format(Locale.ROOT, "%04d", sequence);
    }
}
