package io.devcon5.metrics.demo5;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.metrics.Constants.PARALLELISM;

import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.PercentilesVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class PercentilesDatasource {

    public static void main(String... args) {

        final Vertx vertx = Vertx.vertx();

        final JsonObject mongoConfig = new JsonObject().put("host", "localhost")
                                                       .put("db_name", "rtm")
                                                       .put("col_name", "measurements");
        final JsonObject httpConfig = new JsonObject().put("http.port", 3340);

        int parallelism = Runtime.getRuntime().availableProcessors() *4;

        vertx.deployVerticle(PercentilesVerticle.class.getName(),
                             new DeploymentOptions().setInstances(parallelism)
                                                    .setWorker(true)
                                                    .setConfig(new JsonObject().put(ADDRESS, "/queryChunks")
                                                                               .put(MONGO, mongoConfig)));
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
