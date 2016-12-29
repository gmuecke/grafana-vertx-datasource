package io.devcon5.metrics;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;

import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.SimpleTimeSeriesVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class SimpleGrafanaDatasource extends AbstractVerticle {

    public static void main(String... args) {

        final Vertx vertx = Vertx.vertx();

        final JsonObject mongoConfig = new JsonObject().put("host", "localhost")
                                                 .put("db_name", "rtm")
                                                 .put("col_name", "measurements");
        final JsonObject httpConfig = new JsonObject().put("http.port", 3339);

        vertx.deployVerticle(new SimpleTimeSeriesVerticle(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/query")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(new AnnotationVerticle(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/annotations")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(new LabelVerticle(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/search")
                                                                               .put(MONGO, mongoConfig)));
        vertx.deployVerticle(new HttpServerVerticle(), new DeploymentOptions().setConfig(httpConfig));
    }

}
