package io.devcon5.metrics.demo4;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.metrics.Constants.PARALLELISM;

import io.devcon5.metrics.verticles.AggregateTimeSeriesVerticle;
import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class AggregatingGrafanaDatasource extends AbstractVerticle {

    public static void main(String... args) {

        final Vertx vertx = Vertx.vertx();

        final JsonObject mongoConfig = new JsonObject().put("host", "localhost")
                                                       .put("db_name", "rtm")
                                                       .put("col_name", "measurements");
        final JsonObject httpConfig = new JsonObject().put("http.port", 3339);
        int parallelism = Runtime.getRuntime().availableProcessors();

        vertx.deployVerticle(AggregateTimeSeriesVerticle.class.getName(),
                             new DeploymentOptions().setInstances(1)
                                                    .setConfig(new JsonObject().put(ADDRESS, "/queryChunks")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(SplitMergeTimeSeriesVerticle.class.getName(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/query")
                                                                               .put(DELEGATE_ADDRESS, "/queryChunks")
                                                                               .put(PARALLELISM, 8)
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
