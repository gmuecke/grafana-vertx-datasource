package io.devcon5.metrics.verticles;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.Aggregation.*;
import static io.devcon5.vertx.mongo.Filters.$and;
import static io.devcon5.vertx.mongo.Filters.$gte;
import static io.devcon5.vertx.mongo.Filters.$in;
import static io.devcon5.vertx.mongo.Filters.$lte;
import static io.devcon5.vertx.mongo.JsonFactory.arr;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.devcon5.metrics.util.IntervalParser;
import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;

/**
 *
 */
public class AggregateTimeSeriesVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(AggregateTimeSeriesVerticle.class);

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

        // get the parameters from the query
        final Range range = rangeParser.parse(query.getJsonObject("range").getString("from"),
                                              query.getJsonObject("range").getString("to"));
        final JsonArray targets = query.getJsonArray("targets")
                                       .stream()
                                       .map(o -> ((JsonObject) o).getString("target"))
                                       .collect(toJsonArray());
        final long interval = intervalParser.parseToLong(query.getString("interval"));
        final int maxDataPoints = query.getInteger("maxDataPoints");

        //build the query and options
        long start = System.currentTimeMillis();

        /* the mongo aggregation pipeline:
        [
            {
                "$match": {
                    "$and": [
                        {"t.name": {"$in": targets}},
                        {"n.begin": {"$gte": range.start}},
                        {"n.begin": {"$lte": range.end}}
                    ]
                }
            },
            {
                "$group": {
                    "_id": {
                        "name": "$t.name",
                        //get the interval number for each datapoint
                        // intervalNo = trunc( (n.begin - range.start) / interval )
                        "interval": {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}},
                        //get the interval timestamp
                        // ts = range.start + intervalNo * interval
                        "ts": {"$add": [range.start, {"$multiply": [interval, {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}}]}]}
                    },
                    "count": {"$sum": 1},
                    "sum": {"$sum": "$n.value"},
                    "avg": {"$avg": "$n.value"},
                    "min": {"$min": "$n.value"},
                    "max": {"$max": "$n.value"}
                }
            },
            {"$limit": maxDataPoints},
            {
                "$sort": {"_id.interval": 1}
            }
        ]
         */
        /* in java
        // without helper methods:

        JsonArray pipeline = new JsonArray();
        pipeline.add(new JsonObject().put("$match",
            new JsonObject().put("$and", new JsonArray().add(new JsonObject().put("t.name", new JsonObject().put("$in", targets)))
                                                        .add(new JsonObject().put("n.begin", new JsonObject().put("$gte", range.getStart())))
                                                        .add(new JsonObject().put("n.begin", new JsonObject().put("$lte", range.getStart())))
            )))
                .add(...)

        */
        //with helper methods
        JsonObject cmd = obj().put("aggregate", collectionName)
                              .put("pipeline",
                                   arr($match($and(obj("t.name", $in(targets)),
                                                   obj("n.begin", $gte(range.getStart())),
                                                   obj("n.begin", $lte(range.getEnd())))),
                                       $group(obj().put("_id",
                                                        obj().put("name", "$t.name")
                                                             .put("interval",
                                                                  $trunc($divide($subtract("$n.begin",
                                                                                           range.getStart()),
                                                                                 interval)))
                                                             .put("ts",
                                                                  $add(range.getStart(),
                                                                       $multiply(interval,
                                                                                 $trunc($divide($subtract("$n.begin",
                                                                                                          range.getStart()),
                                                                                                interval))))))
                                                   .put("count", $sum(1))
                                                   .put("sum", $sum("$n.value"))
                                                   .put("avg", $avg("$n.value"))
                                                   .put("min", $min("$n.value"))
                                                   .put("max", $max("$n.value"))),
                                       $limit(maxDataPoints),
                                       $sort(obj("_id.interval", 1))));

        LOG.info("CMD:\n{}", cmd.encodePrettily());

        client.runCommand("aggregate", cmd, res -> {
            long end = System.currentTimeMillis();

            if (res.succeeded()) {
                JsonObject resObj = res.result();
                final JsonArray result = resObj.getJsonArray("result");

                //option 1 - with one aggregation
                JsonArray response = singleAggregation(targets, result, "avg");
                //option 2 - with multiple aggregations + dynamic targets
                //JsonArray response = singleAggregation(targets, result, "avg", "sum", "count", "min", "max");

                LOG.debug("Sending response with {} timeseries and {} datapoints (after {} ms)",
                          response.size(),
                          response.stream()
                                  .map(o -> ((JsonObject) o).getJsonArray("datapoints"))
                                  .collect(Collectors.summingInt(JsonArray::size)),
                          (end - start));
                msg.reply(response);
            } else {
                LOG.error("Annotation query failed", res.cause());
                msg.reply(arr());
            }
        });

    }

    private JsonArray singleAggregation(final JsonArray targets, final JsonArray result, final String... aggregations) {

        return targets.stream()
                      .flatMap(target -> Arrays.stream(aggregations)
                                               .map(agg -> obj().put("target", target + "_" + agg)
                                                                .put("datapoints", mapDatapoints(result, target, agg))))
                      .collect(toJsonArray());

    }

    private JsonArray mapDatapoints(final JsonArray result, final Object target, final String agg) {

        return result.stream()
                     .map(r -> (JsonObject) r)
                     .filter(r -> target.equals(r.getJsonObject("_id").getString("name"))) //filter by target name
                     .map(dp -> arr(dp.getLong(agg), dp.getJsonObject("_id").getLong("ts"))) //a datapoint
                     .collect(toJsonArray());
    }
}
