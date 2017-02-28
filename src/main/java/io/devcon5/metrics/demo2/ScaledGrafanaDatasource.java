package io.devcon5.metrics.demo2;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.PARALLELISM;

import io.devcon5.metrics.util.Config;
import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.SimpleTimeSeriesVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class ScaledGrafanaDatasource extends AbstractVerticle {

    public static void main(String... args) {

        final Vertx vertx = Vertx.vertx();
        final JsonObject config = Config.fromFile("config/demo2.json");

        vertx.deployVerticle(new ScaledGrafanaDatasource(), new DeploymentOptions().setConfig(config));
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();

        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        DeploymentOptions chunkOpts = new DeploymentOptions().setConfig(config.copy().put(ADDRESS, "/queryChunk"));

        vertx.deployVerticle(SimpleTimeSeriesVerticle.class.getName(),
                             //CHOOSE 1 :)
                             chunkOpts.setInstances(config.getInteger(PARALLELISM)));
                             //chunkOpts);
        vertx.deployVerticle(SplitMergeTimeSeriesVerticle.class.getName(), opts);
        vertx.deployVerticle(AnnotationVerticle.class.getName(), opts);
        vertx.deployVerticle(LabelVerticle.class.getName(), opts);
        vertx.deployVerticle(HttpServerVerticle.class.getName(), opts);
    }
}
