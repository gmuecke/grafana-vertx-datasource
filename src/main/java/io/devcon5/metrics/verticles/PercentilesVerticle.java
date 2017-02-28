package io.devcon5.metrics.verticles;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.Filters.$and;
import static io.devcon5.vertx.mongo.Filters.$gte;
import static io.devcon5.vertx.mongo.Filters.$in;
import static io.devcon5.vertx.mongo.Filters.$lte;
import static io.devcon5.vertx.mongo.JsonFactory.arr;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static java.util.stream.Collector.Characteristics.CONCURRENT;
import static java.util.stream.Collector.Characteristics.UNORDERED;
import static java.util.stream.Collectors.toSet;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;

import io.devcon5.metrics.util.IntervalParser;
import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * This verticle calculates percentiles in the JVM using Apache Commons Math. This means that all datapoints
 * are retrieved from the DB and held in memory for processing
 */
public class PercentilesVerticle extends AbstractVerticle {

    protected static final Set<Collector.Characteristics> CHARACTERISTICS = Stream.of(UNORDERED,CONCURRENT)
                                                                                  .collect(toSet());

    private static final Logger LOG = getLogger(PercentilesVerticle.class);

    private MongoClient client;

    private String collectionName;

    private RangeParser rangeParser = new RangeParser();

    private IntervalParser intervalParser = new IntervalParser();

    private String address;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        final JsonObject mongoConfig = config().getJsonObject(MONGO);
        this.client = MongoClient.createShared(vertx, mongoConfig);
        this.collectionName = mongoConfig.getString("col_name");
        this.address = config().getString(ADDRESS, "/query");
        vertx.eventBus().consumer(address, this::queryTimeSeries);

    }

    /**
     * Searches datapoints within range and for selected targets
     *
     * @param msg
     */
    void queryTimeSeries(final Message<JsonObject> msg) {

        final JsonObject query = msg.body();
        LOG.debug("{}\n{}", address, query.encodePrettily());

        // get the paramsters from the query
        final Range range = rangeParser.parse(query.getJsonObject("range").getString("from"),
                query.getJsonObject("range").getString("to"));
        final long interval = intervalParser.parseToLong(query.getString("interval"));
        final JsonArray targets = query.getJsonArray("targets")
                                       .stream()
                                       .map(o -> ((JsonObject) o).getString("target"))
                                       .collect(toJsonArray());

        //build the query and options
        final JsonObject tsQuery = $and(obj("n.begin", $gte(range.getStart())),
                obj("n.begin", $lte(range.getEnd())),
                obj("t.name", $in(targets)));

        final FindOptions findOptions = new FindOptions().setFields(obj().put("t.name", 1)
                                                                         .put("n.begin", 1)
                                                                         .put("n.value", 1)
                                                                         .put("_id", 0)).setSort(obj("n.begin", 1));

        long start = System.currentTimeMillis();
        //execute search and process response
        client.findWithOptions(collectionName, tsQuery, findOptions, result -> {

            if (result.succeeded()) {
                JsonArray resp = processResponse(range, r -> range.splitEvery(interval), targets, result);
                long end = System.currentTimeMillis();
                LOG.debug(
                        "Sending response with {} timeseries and {} datapoints (after {} ms)",
                        resp.size(),
                        resp.stream()
                            .map(o -> ((JsonObject) o).getJsonArray("datapoints"))
                            .collect(Collectors.summingInt(JsonArray::size)),
                        (end - start));
                msg.reply(resp);

            } else {
                LOG.error("Annotation query failed", result.cause());
                msg.reply(arr());
            }

        });
    }

    private JsonArray processResponse(final Range range,
                                      final Function<Range, List<Range>> rangeSplitter,
                                      final JsonArray targets,
                                      final AsyncResult<List<JsonObject>> result) {

        final List<Range> intervals = rangeSplitter.apply(range);
        final List<JsonObject> datapoints = result.result();

        return targets.stream()
                      .parallel()
                      .map(Object::toString)
                      .flatMap(targetName -> calculatePecentileForTarget(targetName, intervals, datapoints))
                      .collect(new DatapointsResultCollector());
    }

    /**
     * Creates a stream of percentiles timeseries for the specified target using the given datapoints.
     *
     * @param targetName
     * @param intervals
     * @param datapoints
     *
     * @return
     */
    private Stream<JsonObject> calculatePecentileForTarget(final String targetName,
                                                           final List<Range> intervals,
                                                           final List<JsonObject> datapoints) {

        return Stream.of("pcl99", "pcl95", "pcl90", "pcl80", "pcl50")
                     .parallel()
                     .map(label -> getTargetLabelMapper(datapoints, intervals).apply(targetName, label));
    }

    /**
     * Creates a mapper to map the datapoints to a timeseries result of the format
     * <pre>
     *     {
     *         "target": target_statsLabel,
     *         "datapoints": [ [val1, ts1], [val2, ts2] ]
     *     }
     * </pre>
     *
     * @param datapoints
     * @param intervals
     *
     * @return
     */
    private BiFunction<String, String, JsonObject> getTargetLabelMapper(final List<JsonObject> datapoints,
                                                                        final List<Range> intervals) {

        return (target, label) -> obj().put("target", target + "_" + label)
                                       .put("datapoints", statistics(datapoints, target, intervals, label));
    }

    /**
     * Calculates the statistics specified by the label. The result is an array of arrays with two values, the first
     * is the calculated statistics value, the second is the timestamp.
     * <pre>
     *     [ [val1, ts1], [val2, ts2],... ]
     * </pre>
     *
     * @param datapoints
     * @param targetName
     * @param intervals
     * @param statsLabel
     *
     * @return
     */
    private JsonArray statistics(List<JsonObject> datapoints,
                                 String targetName,
                                 List<Range> intervals,
                                 String statsLabel) {

        return intervals.stream()
                        .map(range -> {

                            DescriptiveStatistics stats = calculateStatsForRange(datapoints, targetName, range);
                            switch (statsLabel) {
                                case "min":
                                    return datapoint(stats.getMin(), range.getStart());
                                case "pcl50":
                                    return datapoint(stats.getPercentile(50), range.getStart());
                                case "pcl80":
                                    return datapoint(stats.getPercentile(80), range.getStart());
                                case "pcl90":
                                    return datapoint(stats.getPercentile(90), range.getStart());
                                case "pcl95":
                                    return datapoint(stats.getPercentile(95), range.getStart());
                                case "pcl99":
                                    return datapoint(stats.getPercentile(99), range.getStart());
                                case "max":
                                    return datapoint(stats.getMax(), range.getStart());
                                default:
                                    return datapoint(0d, 0);
                            }
                        })
                        .filter(a -> !a.isEmpty())
                        .collect(toJsonArray());
    }

    /**
     * Calculates the descriptive statistics for the given set of datapoints
     *
     * @param datapoints
     * @param targetName
     * @param range
     *
     * @return
     */
    private DescriptiveStatistics calculateStatsForRange(List<JsonObject> datapoints, String targetName, Range range) {

        return datapoints.stream()
                         .filter(js -> targetName.equals(js.getJsonObject("t").getString("name"))
                                 && range.contains(js.getJsonObject("n").getLong("begin")))
                         .map(js -> js.getJsonObject("n").getLong("value"))
                         .map(Long::doubleValue)
                         .collect(descStatsCollector());
    }

    /**
     * Creates an array denoting a datapoint. The array has two fields, the first indicates the value, the second
     * the timestamp
     *
     * @param value
     * @param ts
     *
     * @return
     */
    private JsonArray datapoint(final Double value, final long ts) {

        if (value.isNaN()) {
            return arr();
        }
        return arr(value, ts);
    }

    /**
     * Creates a collector to collect double valuees into a DescriptiveStats to calculate specific statistic
     *
     * @return
     */
    private Collector<Double, DescriptiveStatistics, DescriptiveStatistics> descStatsCollector() {

        return Collector.of(DescriptiveStatistics::new,
                DescriptiveStatistics::addValue,
                (s1, s2) -> DoubleStream.concat(DoubleStream.of(s1.getValues()),
                        DoubleStream.of(s1.getValues()))
                                        .collect(DescriptiveStatistics::new,
                                                DescriptiveStatistics::addValue,
                                                (ds1, ds2) -> DoubleStream.of(s2.getValues())
                                                                          .forEach(s1::addValue)));
    }

    /**
     * A collector to collect multiple Json object representing a time series of the format
     * <pre>
     *     {
     *         "target": targetName,
     *         "datapoints": [ [v,ts], [v,ts],...]
     *     }
     * </pre>
     * into a single array with combined timeseries. Timeseries with same target labels are merged.
     */
    private static class DatapointsResultCollector implements Collector<JsonObject, Map<String, JsonArray>, JsonArray> {

        @Override
        public Supplier<Map<String, JsonArray>> supplier() {

            return ConcurrentHashMap::new;
        }

        @Override
        public BiConsumer<Map<String, JsonArray>, JsonObject> accumulator() {

            return (a, o) -> {
                if (a.containsKey(o.getString("target"))) {
                    a.get(o.getString("target")).addAll(o.getJsonArray("datapoints"));
                } else {
                    a.put(o.getString("target"), o.getJsonArray("datapoints"));
                }
            };
        }

        @Override
        public BinaryOperator<Map<String, JsonArray>> combiner() {

            return (m1, m2) -> Stream.concat(m1.entrySet().stream(), m2.entrySet().stream())
                                     .collect(Collectors.groupingBy(Map.Entry::getKey,
                                             HashMap::new,
                                             Collector.of(JsonArray::new,
                                                     (arr, e) -> arr.addAll(e.getValue()),
                                                     JsonArray::addAll)));
        }

        @Override
        public Function<Map<String, JsonArray>, JsonArray> finisher() {

            return m -> m.entrySet()
                         .stream()
                         .map(e -> obj("target", e.getKey()).put("datapoints", e.getValue()))
                         .collect(toJsonArray());
        }

        @Override
        public Set<Characteristics> characteristics() {

            return CHARACTERISTICS;
        }
    }
}
