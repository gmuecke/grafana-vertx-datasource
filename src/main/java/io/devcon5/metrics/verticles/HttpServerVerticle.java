package io.devcon5.metrics.verticles;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Optional;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;

/**
 *
 */
public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(HttpServerVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        final Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/").handler(this::ping);
        router.options("/*").handler(this::options);

        //route all other messages to the event bus
        router.get("/*").handler(ctx -> {
            LOG.info("GET {}", ctx.normalisedPath());
            ctx.next();
        });
        router.post("/*").handler(ctx -> {
            LOG.info("POST {}", ctx.normalisedPath());
            ctx.next();
        });
        router.post("/*")
              .handler(ctx -> vertx.eventBus()
                                   .send(ctx.normalisedPath(),
                                         ctx.getBodyAsJson(),
                                         reply -> sendResponse(ctx, Optional.ofNullable(reply.result()).map(res -> res.body().toString()).orElse("[]"))));
        router.get("/*")
              .handler(ctx -> vertx.eventBus()
                                   .send(ctx.normalisedPath(),
                                         "GET",
                                         reply -> sendResponse(ctx,
                                                               Optional.ofNullable(reply.result())
                                                                       .map(res -> res.body().toString())
                                                                       .orElse(""))));

        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(config().getInteger("http.port", 3339), result -> {
                 if (result.succeeded()) {
                     LOG.info("Server listening on port {}", config().getInteger("http.port", 3339));
                     startFuture.complete();
                 } else {
                     startFuture.fail("FAIL" + result.cause());
                 }
             });
    }

    protected void sendResponse(RoutingContext ctx, String response) {

        LOG.trace("> Sending response \n{}", response);
        ctx.response().putHeader("content-type", "application/json; charset=utf-8").end(response);
    }

    /////////////// Helper methods
    private void options(RoutingContext ctx) {

        ctx.response()
           .putHeader("Access-Control-Allow-Headers", "accept, content-type")
           .putHeader("Access-Control-Allow-Methods", "POST")
           .putHeader("Access-Control-Allow-Origin", "*")
           .end();
    }

    private void ping(RoutingContext routingContext) {

        routingContext.response().putHeader("content-type", "text/html").end("Grafana DistributedDatasource");
    }

}
