package io.devcon5.metrics.demo0;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 *
 */
public class BackupVerticle extends AbstractVerticle {

    /*

     */
    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new BackupVerticle());
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        HttpServer http = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/hello").handler(ctx -> ctx.response().end("World " + System.currentTimeMillis()));

        http.requestHandler(router::accept).listen(11011, result -> {
            if(result.succeeded()){
                System.out.println("Listening on port 11011");
            } else {
                throw new RuntimeException("Server start failed");
            }
        });

    }
}
