package io.devcon5.metrics.demo7;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static org.slf4j.LoggerFactory.getLogger;

import io.devcon5.metrics.verticles.HttpServerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

/**
 *
 */
public class JSMongoDatasource {

    private static final Logger LOG = getLogger(JSMongoDatasource.class);

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();

        final JsonObject mongoConfig = new JsonObject().put("connection_string", "mongodb://localhost:27017/rtm")
                                                       .put("db_name", "rtm");
        final JsonObject httpConfig = new JsonObject().put("http.port", 5050);

        vertx.deployVerticle("js:io/devcon5/metrics/demo7/CollectionsVerticle.js",
                new DeploymentOptions().setConfig(new JsonObject().put(ADDRESS, "/collections")
                                                                  .put(MONGO, mongoConfig)));

        vertx.deployVerticle(HttpServerVerticle.class.getName(), new DeploymentOptions().setConfig(httpConfig));
    }
}
