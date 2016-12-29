package io.devcon5.metrics.demo3;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.verticles.SimpleTimeSeriesVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class WorkerNode {

    private static final Logger LOG = getLogger(WorkerNode.class);

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
        int parallelism = Runtime.getRuntime().availableProcessors() / 4;

        vertx.deployVerticle(SimpleTimeSeriesVerticle.class.getName(),
                             new DeploymentOptions().setInstances(parallelism)
                                                    .setConfig(new JsonObject().put(ADDRESS, "/queryChunks")
                                                                               .put(MONGO, mongoConfig)));
    }

}
