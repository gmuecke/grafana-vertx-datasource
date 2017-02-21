package io.devcon5.metrics.demo7;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.DELEGATE_ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.JsonFactory.obj;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class JSMongoDatasource extends AbstractVerticle {

    private static final Logger LOG = getLogger(JSMongoDatasource.class);

    public static void main(String... args) {

        Vertx vertx = Vertx.vertx();

        final JsonObject config = obj().put("mongoConfig",
                                            obj().put("connection_string", "mongodb://localhost:27017/rtm")
                                                 .put("db_name", "rtm")) //
                                       .put("httpConfig", obj("http.port", 5050));

        vertx.deployVerticle(JSMongoDatasource.class.getName(), new DeploymentOptions().setConfig(config));
    }

    @Override
    public void start() throws Exception {

        JsonObject config = Vertx.currentContext().config();

        vertx.deployVerticle("js:io/devcon5/metrics/demo7/CollectionsVerticle.js",
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/collections")
                                                                               .put(MONGO,
                                                                                    config.getJsonObject("mongoConfig"))));
        vertx.deployVerticle("js:io/devcon5/metrics/demo7/QueryVerticle.js",
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/queryTarget")
                                                                               .put(MONGO,
                                                                                    config.getJsonObject("mongoConfig"))));
        vertx.deployVerticle("js:io/devcon5/metrics/demo7/AggregationVerticle.js");


        vertx.deployVerticle(TargetSplitVerticle.class.getName(),
                             new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/query")
                                                                               .put(DELEGATE_ADDRESS, "/queryTarget")
                                                                               .put(MONGO,
                                                                                    config.getJsonObject("mongoConfig"))));
        vertx.deployVerticle(HttpServerVerticle.class.getName(),
                             new DeploymentOptions().setConfig(config.getJsonObject("httpConfig")));

    }
}
