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
import static org.slf4j.LoggerFactory.getLogger;

import java.util.stream.Collectors;

import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;

/**
 *
 */
public class SimpleTimeSeriesVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(SimpleTimeSeriesVerticle.class);

    private MongoClient client;
    private String collectionName;
    private RangeParser rangeParser = new RangeParser();
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

        //execute search and process response
        client.findWithOptions(collectionName, tsQuery, findOptions, result -> {
            if (result.succeeded()) {
                JsonArray response = targets.stream()
                                            //  every timeseries is an {"target": label, "datapoints": [[value, ts],... ]}
                                            .map(target -> obj().put("target", target)
                                                                .put("datapoints",
                                                                     result.result()
                                                                           .stream()
                                                                           .filter(r -> target.equals(r.getJsonObject(
                                                                                   "t").getString("name")))
                                                                           .map(json -> json.getJsonObject("n"))
                                                                           //produce a grafana json datapoint [value,timestamp]
                                                                           .map(dp -> arr(dp.getLong("value"),
                                                                                          dp.getLong("begin")))
                                                                           //collect all datapoint arrays to an array of arrays
                                                                           .collect(toJsonArray())))
                                            .collect(toJsonArray());
                LOG.debug("Sending response with {} timeseries and {} datapoints",
                          response.size(),
                          response.stream()
                                  .map(o -> ((JsonObject) o).getJsonArray("datapoints"))
                                  .collect(Collectors.summingInt(JsonArray::size)));
                msg.reply(response);
            } else {
                LOG.error("Annotation query failed", result.cause());
                msg.reply(arr());
            }
        });
    }

}
