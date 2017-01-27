package io.devcon5.metrics.demo7;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;

import io.devcon5.metrics.util.IntervalParser;
import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.devcon5.metrics.util.Tuple;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class TargetSplitVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(TargetSplitVerticle.class);

    private RangeParser rangeParser = new RangeParser();
    private IntervalParser intervalParser = new IntervalParser();

    private int cpuCores;

    private String queryTargetAddress;

    private String address;
    private String dbName;

    @Override
    public void start() throws Exception {

        this.address = config().getString(ADDRESS, "/query");
        this.queryTargetAddress = config().getString(DELEGATE_ADDRESS, "/queryTarget");
        this.dbName = config().getJsonObject(MONGO).getString("db_name");

        vertx.eventBus().consumer("/" + this.dbName + this.address, this::queryTimeSeries);
    }

    /**
     * Searches datapoints within range and for selected targets
     *
     * @param msg
     */
    void queryTimeSeries(final Message<JsonObject> msg) {

        long start = System.currentTimeMillis();
        final JsonObject query = msg.body();
        LOG.debug("{}\n{}", address, query.encodePrettily());

        // get the parameters from the query
        final Range range = rangeParser.parse(query.getJsonObject("range").getString("from"),
                                              query.getJsonObject("range").getString("to"));
        final long interval = intervalParser.parseToLong(query.getString("interval"));
        final int maxDataPoints = query.getInteger("maxDataPoints");
        final String tsField = query.getString("tsField");
        final JsonArray targets = query.getJsonArray("targets");
        final JsonObject rangeObj = obj().put("from", range.getStart())
                                         .put("to", range.getEnd());

        List<Future> futures = targets.stream()
                                    .map(target -> Tuple.of(obj().put("range", rangeObj)
                                                             .put("interval", interval)
                                                             .put("maxDataPoints", maxDataPoints)
                                                             .put("tsField", tsField)
                                                             .put("target", target),
                                                        Future.<Message<JsonObject>>future()))
                                    .map(tup -> {
                                        vertx.eventBus()
                                             .send("/" + this.dbName + queryTargetAddress, tup.getFirst(), tup.getSecond().completer());
                                        return tup.getSecond();
                                    })
                                    .collect(toList());

        CompositeFuture.all(futures).setHandler(result -> {
            if (result.succeeded()) {
                msg.reply(result.result()
                                .list()
                                .stream()
                                .map(o -> (Message) o)
                                .map(m -> (JsonObject) m.body())
                                .collect(toJsonArray()));
                LOG.info("TIME: response sent after {} ms", (System.currentTimeMillis() - start));
            } else {
                LOG.warn("Could not process query", result.cause());
            }
        });
    }

}
