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

import java.util.stream.Collectors;
import org.slf4j.Logger;

import io.devcon5.metrics.util.IntervalParser;
import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

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
        JsonObject cmd = obj().put("aggregate", collectionName)
                              .put("pipeline",
                                   arr($match($and(obj("t.name", $in(targets)),
                                                   obj("n.begin", $gte(range.getStart())),
                                                   obj("n.begin",$lte(range.getEnd())))),
                                       $group(obj().put("_id",
                                                        obj().put("name", "$t.name")
                                                             .put("interval",$trunc($divide($subtract("$n.begin", range.getStart()),interval)))
                                                             .put("ts", $add(range.getStart(),$multiply(interval,$trunc($divide($subtract("$n.begin", range.getStart()),interval))))))
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

                JsonArray response = targets.stream()
                                            .map(target -> obj().put("target", target)
                                                                .put("datapoints",
                                                                     result.stream()
                                                                           .map(r -> (JsonObject) r)
                                                                           .filter(r -> target.equals(r.getJsonObject(
                                                                                   "_id").getString("name")))
                                                                           .map(dp -> arr(dp.getLong("avg"),
                                                                                          dp.getJsonObject("_id")
                                                                                            .getLong("ts")))
                                                                           .collect(toJsonArray())))
                                            .collect(toJsonArray());
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

}
