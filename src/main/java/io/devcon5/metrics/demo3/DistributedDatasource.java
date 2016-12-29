package io.devcon5.metrics.demo3;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.metrics.Constants.PARALLELISM;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 * This Vertx instance and the event bus covers only the label, annoations, query split&merge and the http
 * server. The Verticles for processing the chunk requests are deployed on two different instances,
 * (WorkerNode & DataBus2)
 */
public class DistributedDatasource {

    private static final Logger LOG = getLogger(DistributedDatasource.class);

    public static void main(String... args) {

        VertxOptions options = new VertxOptions().setClustered(true)
                                                 .setHAEnabled(true)
                                                 .setHAGroup("dev");
        Vertx.clusteredVertx(options, res -> {
            if (res.succeeded()) {
                Vertx vertx = res.result();
                deployVerticles(vertx);

                LOG.info("Vertx Cluster running");
            } else {
                LOG.error("Creating Vertx cluster failed", res.cause());
            }
        });

    }

    private static void deployVerticles(final Vertx vertx) {
        final JsonObject mongoConfig = new JsonObject().put("host", "localhost")
                                                       .put("db_name", "rtm")
                                                       .put("col_name", "measurements");
        final JsonObject httpConfig = new JsonObject().put("http.port", 3339);
        int parallelism = Runtime.getRuntime().availableProcessors() * 2;

        vertx.deployVerticle(SplitMergeTimeSeriesVerticle.class.getName(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/query")
                                                                               .put(DELEGATE_ADDRESS, "/queryChunks")
                                                                               .put(PARALLELISM, parallelism)
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(AnnotationVerticle.class.getName(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/annotations")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(LabelVerticle.class.getName(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/search")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(HttpServerVerticle.class.getName(), new DeploymentOptions().setConfig(httpConfig));
    }

}
