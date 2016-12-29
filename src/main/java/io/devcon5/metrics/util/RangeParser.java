package io.devcon5.metrics.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Parser to parse a range from two String date representations.
 * The parser accepts the following date formats
 * <ul>
 *     <li><code>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</code> - Grafana format</li>
 * </ul>
 */
public class RangeParser {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * Creates a range from the specified dates.
     * @param from
     *  the point in time where the range starts. This should be before the to-date.
     * @param to
     *  the point in time where range ends. This should be after the from-date.
     * @return
     *  a range representation with a start time and a duration
     */
    public Range parse(String from, String to) {
        //i.e. 2016-10-18T13:16:33.733Z
        LocalDateTime fromDateTime = LocalDateTime.parse(from, ISO_DATE);
        LocalDateTime toDateTime = LocalDateTime.parse(to, ISO_DATE);

        return new Range(fromDateTime.toInstant(ZoneOffset.UTC).toEpochMilli(),
                         toDateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
    }



}
