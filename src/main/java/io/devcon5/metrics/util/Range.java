package io.devcon5.metrics.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a temporal duration starting at a specific point in time.
 */
public class Range {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private long start;

    private long end;

    private long duration;

    public Range(long start, long end) {

        this.start = start;
        this.end = end;
        this.duration = end - start;
    }

    /**
     * The timestamp of the point in time where the range starts (inclusive)
     * @return
     *  the timestamp in millis
     */
    public long getStart() {

        return start;
    }

    /**
     * Returns the start timestamp as formatted String
     * @return
     *  a timestamp in the format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     */
    public String getStartString() {

        return toString(this.start);
    }

    /**
     * The timestamp  of the point in time where range ends (exclusive)
     * @return
     *  the timestamp in millis
     */
    public long getEnd() {

        return end;
    }


    /**
     * Returns the end timestamp as formatted String
     * @return
     *  a timestamp in the format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     */
    public String getEndString() {

        return toString(this.end);
    }

    /**
     * The duration in milliseconds of the range
     * @return
     *  duration in millis
     */
    public long getDuration() {

        return duration;
    }

    /**
     * Splits the Range into sub-ranges of the specified interval length.
     *
     * @param interval
     *         the length of the sub-ranges in milliseconds
     *
     * @return
     *  a list of subranges
     */
    public List<Range> splitEvery(long interval) {

        return IntStream.range(0, (int) (duration / interval))
                        .mapToObj(i -> new Range(start + i * interval, start + (i + 1) * interval))
                        .collect(Collectors.toList());
    }

    /**
     * Splits the Range into equal sub-ranges.
     *
     * @param numChunks
     *         number of chunks
     *
     * @return
     *  a list of subranges
     */
    public List<Range> split(int numChunks) {

        final long interval = duration / numChunks;
        return IntStream.range(0, numChunks)
                        .mapToObj(i -> new Range(start + i * interval, start + (i + 1) * interval))
                        .collect(Collectors.toList());
    }

    /**
     * Checks if a point in time, specified by its long-valued timestamp, is within this range.
     * @param longValue
     *  a long timestamp of a point in time
     * @return
     *  true, if the timevalue is within the range, including the lower border, excluding the upper border.
     */
    public boolean contains(long longValue) {
        return start <= longValue && end > longValue;
    }

    /**
     * Converts the range into a json string with from and to property.
     * @return
     */
    public String toJson(){
        return "{\"from\":\"" + getStartString() + "\",\"to\":\"" + getEndString() + "\"}";
    }

    private String toString(long timestamp) {

        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC).format(ISO_DATE);
    }
}
