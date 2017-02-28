package io.devcon5.metrics.demo6;

import static io.devcon5.metrics.Constants.ADDRESS;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.util.Config;
import io.devcon5.metrics.verticles.AnnotationVerticle;
import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.devcon5.metrics.verticles.LabelVerticle;
import io.devcon5.metrics.verticles.SplitMergeTimeSeriesVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class JSPercentilesDatasource extends AbstractVerticle{

    private static final Logger LOG = getLogger(JSPercentilesDatasource.class);

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();

        final JsonObject config = Config.fromFile("config/demo6.json");
        vertx.deployVerticle(new JSPercentilesDatasource(), new DeploymentOptions().setConfig(config));
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();
        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        DeploymentOptions chunkOpts = new DeploymentOptions().setConfig(config.copy().put(ADDRESS, "/queryChunk"))
                //.setInstances(config.getInteger(PARALLELISM))
                //.setWorker(true)
                ;

        vertx.deployVerticle("js:io/devcon5/metrics/demo6/AggregatePercentilesVerticle.js",chunkOpts, result -> {
            if (result.succeeded()) {
                LOG.info("JS Verticle successfully deployed {}", result.result());
            } else {
                LOG.error("Failed to deploy JS Verticle", result.cause());
            }
        });
        vertx.deployVerticle(SplitMergeTimeSeriesVerticle.class.getName(), opts);
        vertx.deployVerticle(AnnotationVerticle.class.getName(), opts);
        vertx.deployVerticle(LabelVerticle.class.getName(), opts);
        vertx.deployVerticle(HttpServerVerticle.class.getName(), opts);
    }
}
