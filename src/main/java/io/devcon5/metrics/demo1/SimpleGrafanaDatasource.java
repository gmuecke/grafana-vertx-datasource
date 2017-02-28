package io.devcon5.metrics.demo1;

import io.devcon5.metrics.util.Config;
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
        final JsonObject config = Config.fromFile("config/demo1.json");

        vertx.deployVerticle(new SimpleGrafanaDatasource(), new DeploymentOptions().setConfig(config));
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();
        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(new SimpleTimeSeriesVerticle(), opts);
        vertx.deployVerticle(new AnnotationVerticle(), opts);
        vertx.deployVerticle(new LabelVerticle(), opts);
        vertx.deployVerticle(new HttpServerVerticle(), opts);
    }
}
