package io.devcon5.metrics.util;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 *
 */
public class IntervalParser {

    private static final PeriodFormatter YEARS = new PeriodFormatterBuilder().appendDays()
                                                                            .appendSuffix("y")
                                                                            .toFormatter();
    private static final PeriodFormatter WEEKS = new PeriodFormatterBuilder().appendDays()
                                                                            .appendSuffix("w")
                                                                            .toFormatter();

    private static final PeriodFormatter DAYS = new PeriodFormatterBuilder().appendDays()
                                                                            .appendSuffix("d")
                                                                            .toFormatter();
    private static final PeriodFormatter HOURS = new PeriodFormatterBuilder().appendHours()
                                                                             .appendSuffix("h")
                                                                             .toFormatter();
    private static final PeriodFormatter MINUTES = new PeriodFormatterBuilder().appendMinutes()
                                                                               .appendSuffix("m")
                                                                               .toFormatter();
    private static final PeriodFormatter SECONDS = new PeriodFormatterBuilder().appendSeconds()
                                                                               .appendSuffix("s")
                                                                               .toFormatter();
    private static final PeriodFormatter MILLIS = new PeriodFormatterBuilder().appendMillis()
                                                                              .appendSuffix("ms")
                                                                              .toFormatter();

    private static final List<PeriodFormatter> ALL = Collections.unmodifiableList(Arrays.asList(MILLIS,
                                                                                                SECONDS,
                                                                                                MINUTES,
                                                                                                HOURS,
                                                                                                DAYS));

    private static final long DEFAULT = 1000;

    public long parseToLong(String interval) {

        for (PeriodFormatter f : ALL) {
            try {
                return f.parsePeriod(interval).toStandardDuration().getMillis();
            } catch (IllegalArgumentException e) {
                //ommit
            }
        }
        return DEFAULT;
    }

    public Duration parseToDuration(String interval){
        return Duration.ofMillis(parseToLong(interval));
    }
}

