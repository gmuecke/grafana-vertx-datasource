package io.devcon5.metrics.verticles;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.Filters.$and;
import static io.devcon5.vertx.mongo.Filters.$gte;
import static io.devcon5.vertx.mongo.Filters.$lte;
import static io.devcon5.vertx.mongo.JsonFactory.arr;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.util.Range;
import io.devcon5.metrics.util.RangeParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;

/**
 *
 */
public class AnnotationVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(AnnotationVerticle.class);

    private MongoClient client;

    private String collectionName;

    private RangeParser rangeParser = new RangeParser();
    private String address;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        final JsonObject mongoConfig = config().getJsonObject(MONGO);
        this.client = MongoClient.createShared(vertx, mongoConfig);
        this.collectionName = mongoConfig.getString("col_name");
        this.address = config().getString(ADDRESS, "/annotations");
        vertx.eventBus().consumer(address, this::searchAnnotations);
    }

    /**
     * Searches for annotation in the given time range
     *
     * @param msg
     */
    private void searchAnnotations(final Message<JsonObject> msg) {

        final JsonObject annotation = msg.body();
        LOG.debug("{}\n{}",address, annotation.encodePrettily());

        //get the parameter from the request
        final String from = annotation.getJsonObject("range").getString("from");
        final String to = annotation.getJsonObject("range").getString("to");
        final Range range = rangeParser.parse(from, to);
        final JsonObject an = annotation.getJsonObject("annotation");

        //build the query and find options
        final JsonObject annotationQuery = $and(obj(an.getString("query")),
                                                obj("n.begin", $gte(range.getStart())),
                                                obj("n.begin", $lte(range.getEnd())));
        final FindOptions findOptions = new FindOptions().setSort(obj("n.begin", 1)).setLimit(1);

        //query for annotations and map the result
        client.findWithOptions(collectionName, annotationQuery, findOptions, result -> {
            if (result.succeeded()) {
                msg.reply(result.result()
                                .stream()
                                .map(a -> obj().put("annotation", an)
                                               .put("time", a.getJsonObject("n").getLong("begin"))
                                               .put("title", a.getJsonObject("t").getString("name"))
                                               .put("tags", arr()))
                                .collect(toJsonArray()));
            } else {
                LOG.error("Annotation query failed", result.cause());
                msg.reply(arr());
            }
        });
    }
}
