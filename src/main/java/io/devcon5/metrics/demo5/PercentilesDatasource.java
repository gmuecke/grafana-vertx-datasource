package io.devcon5.metrics.demo5;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.PARALLELISM;

import io.devcon5.metrics.util.Config;
import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.PercentilesVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class PercentilesDatasource extends AbstractVerticle {

    public static void main(String... args) {

        final Vertx vertx = Vertx.vertx();

        final JsonObject config = Config.fromFile("config/demo5.json");
        vertx.deployVerticle(new PercentilesDatasource(), new DeploymentOptions().setConfig(config));
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();
        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        DeploymentOptions chunkOpts = new DeploymentOptions().setInstances(config.getInteger(PARALLELISM))
                                                             .setWorker(true)
                                                             .setConfig(config.copy().put(ADDRESS, "/queryChunk"));

        //this verticle is doing the "post-processing"
        vertx.deployVerticle(PercentilesVerticle.class.getName(),chunkOpts);
        //the other verticles
        vertx.deployVerticle(SplitMergeTimeSeriesVerticle.class.getName(), opts);
        vertx.deployVerticle(AnnotationVerticle.class.getName(), opts);
        vertx.deployVerticle(LabelVerticle.class.getName(), opts);
        vertx.deployVerticle(HttpServerVerticle.class.getName(), opts);
    }
}
