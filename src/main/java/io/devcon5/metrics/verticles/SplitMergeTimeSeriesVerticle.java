package io.devcon5.metrics.verticles;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.PARALLELISM;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collector;

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
public class SplitMergeTimeSeriesVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(SplitMergeTimeSeriesVerticle.class);

    private RangeParser rangeParser = new RangeParser();

    private int numChunks;

    private String queryChunkAddress;

    private String address;
    private String postProcessingAddress = null;

    @Override
    public void start() throws Exception {

        this.address = config().getString(ADDRESS, "/query");
        this.queryChunkAddress = config().getString(DELEGATE_ADDRESS, "/queryChunk");
        this.numChunks = config().getInteger(PARALLELISM, Runtime.getRuntime().availableProcessors());

        vertx.eventBus().consumer(this.address, this::queryTimeSeries);
        vertx.eventBus().consumer("registerPostProcessing", this::registerPostprocessing);
    }

    /**
     * Register a single postprocessing eventbus address, only a single post-processor supported at the moment.
     *
     * @param msg
     */
    void registerPostprocessing(final Message<String> msg) {

        String ppAddress = msg.body();
        LOG.info("Registering postprocessing at {}", ppAddress);
        this.postProcessingAddress = ppAddress;
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

        // get the paramsters from the query
        final Range range = rangeParser.parse(query.getJsonObject("range").getString("from"),
                                              query.getJsonObject("range").getString("to"));
        final Integer limit = query.getInteger("maxDataPoints");
        final String interval = query.getString("interval");

        List<Future> futures = range.split(numChunks)
                                    .stream()
                                    .map(rc -> Tuple.of(obj().put("range",
                                                                  obj().put("from", rc.getStartString())
                                                                       .put("to", rc.getEndString()))
                                                             .put("interval", interval)
                                                             .put("maxDataPoints", limit / numChunks)
                                                             .put("targets", query.getJsonArray("targets")),
                                                        Future.<Message<JsonObject>>future()))
                                    .map(tup -> {
                                        vertx.eventBus()
                                             .send(queryChunkAddress, tup.getFirst(), tup.getSecond().completer());
                                        return tup.getSecond();
                                    })
                                    .collect(toList());

        CompositeFuture.all(futures).setHandler(result -> {
            if (result.succeeded()) {
                JsonArray rawResult = result.result()
                                            .list()
                                            .stream()
                                            .map(o -> (Message) o)
                                            .map(m -> (JsonArray) m.body())
                                            .collect(toMergedResult());
                // POST Processing if registered
                if (this.postProcessingAddress != null) {
                    vertx.eventBus().send(this.postProcessingAddress, rawResult, ppReply -> {
                        if (ppReply.succeeded()) {
                            sendReply(msg, rawResult.addAll((JsonArray) ppReply.result().body()), start);
                        } else {
                            LOG.error("PostProcessing failed", ppReply.cause());
                            sendReply(msg, rawResult, start);
                            this.postProcessingAddress = null;
                        }
                    });
                } else {
                    sendReply(msg, rawResult, start);
                }
            } else {
                LOG.warn("Could not process query", result.cause());
            }
        });
    }

    private Collector<? super JsonArray, JsonArray, JsonArray> toMergedResult() {

        return Collector.of(JsonArray::new, (all, arr) -> {
            arr.stream()
               .map(o -> (JsonObject) o)
               .sorted(Comparator.comparing(ts -> ts.getString("target")))
               .forEach(newTs -> {
                   final String target = newTs.getString("target");
                   if (containsObjectWithKeyValue(all, "target", target)) {
                       all.stream()
                          .map(o -> (JsonObject) o)
                          .filter(o -> matchesKeyWithValue(o, "target", target))
                          .forEach(ex -> ex.getJsonArray("datapoints").addAll(newTs.getJsonArray("datapoints")));
                   } else {
                       all.add(newTs);
                   }
               });
        }, JsonArray::addAll);
    }

    private void sendReply(final Message<JsonObject> msg, final JsonArray rawResult, final long start) {

        msg.reply(rawResult);
        LOG.info("TIME: response sent after {} ms", (System.currentTimeMillis() - start));
    }

    private boolean containsObjectWithKeyValue(final JsonArray all, final String key, final String value) {

        return all.stream()
                  .map(o -> (JsonObject) o)
                  .anyMatch(o -> o.containsKey(key) && value.equals(o.getString(key)));
    }

    private boolean matchesKeyWithValue(final JsonObject obj, final String key, final String value) {

        return obj.containsKey(key) && value.equals(obj.getString(key));
    }

}
