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
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class JSAggregateDatasource extends AbstractVerticle{

    private static final Logger LOG = getLogger(JSAggregateDatasource.class);

    public static void main(String... args) {

        final JsonObject config = Config.fromFile("config/demo6.json");

        /*
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new JSAggregateDatasource(), new DeploymentOptions().setConfig(config));
        */
        VertxOptions opts = new VertxOptions().setClustered(true);
        Vertx.clusteredVertx(opts, result -> {
            if(result.succeeded()){
                LOG.info("Cluster running");
                Vertx vertx = result.result();
                vertx.deployVerticle(new JSAggregateDatasource(), new DeploymentOptions().setConfig(config));
            } else {
                LOG.error("Clustering failed");
                throw new RuntimeException(result.cause());
            }
        });
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();
        DeploymentOptions opts = new DeploymentOptions().setConfig(config);
        DeploymentOptions chunkOpts = new DeploymentOptions().setConfig(config.copy().put(ADDRESS, "/queryChunk"))
                                                             //.setInstances(config.getInteger(PARALLELISM))
                                                             //.setWorker(true)
        ;

        vertx.deployVerticle("js:io/devcon5/metrics/demo6/AggregateTimeSeriesVerticle.js",chunkOpts, result -> {
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
