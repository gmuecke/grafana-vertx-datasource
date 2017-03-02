package io.devcon5.metrics.demo7;

import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.TreeMap;

import io.devcon5.metrics.util.Config;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;

/**
 * Time is money, multiply the datapoints with the bitcoin2USD rate at that time
 */
public class BitcoinAdjustedData extends AbstractVerticle {

    private static final Logger LOG = getLogger(BitcoinAdjustedData.class);

    //use a treemap to get datapoints close to a specific timestamp
    private TreeMap<Long, Double> dataset = new TreeMap<>();

    public static void main(String... args) {

        //Note to self
        // run this demo in HA mode, deploy this verticle on a separate node and combine it with demo6

        final JsonObject config = Config.fromFile("config/demo7.json");

        VertxOptions opts = new VertxOptions().setClustered(true);
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                LOG.info("Cluster running");
                Vertx vertx = result.result();
                vertx.deployVerticle(BitcoinAdjustedData.class.getName(),
                                     new DeploymentOptions().setConfig(config).setWorker(false));
            } else {
                LOG.error("Clusterin failed");
                throw new RuntimeException(result.cause());
            }
        });
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        final JsonObject config = Vertx.currentContext().config();
        final String filename = config.getString("bitcoinFile");

        //parser for line-by-line reading the csv file
        final RecordParser lineParser = RecordParser.newDelimited("\n", this::parseBitcoinPriceLine);

        //
        final long start = System.currentTimeMillis();
        vertx.fileSystem().open(filename, new OpenOptions(), result -> {
            if (result.succeeded()) {
                AsyncFile file = result.result();
                //skip the first dates and start ~01/08/2015
                file.setReadPos(903471906L);
                file.handler(lineParser);
                file.endHandler(evt -> {
                    LOG.info("Done reading {} datapoints in {} ms",
                             dataset.size(),
                             (System.currentTimeMillis() - start));
                    LOG.info("Registering PostProcessor");
                    vertx.eventBus().send("registerPostProcessing", "/bitcoin/datapoint");
                    startFuture.complete();
                });
                file.resume();
            } else {
                throw new RuntimeException("can not read file " + filename, result.cause());
            }
        });

        vertx.eventBus().consumer("/bitcoin/datapoint", this::enrichData);

    }

    /**
     * One line has the format:
     * <pre>
     *     timestamp,price,volume
     * </pre>
     * <ul>
     * <li>timestamp is unix timestamp in seconds</li>
     * <li>price is in USD in double</li>
     * <li>volume is in USD in double</li>
     * </ul>
     *
     * @param bufferedLine
     */
    private void parseBitcoinPriceLine(final Buffer bufferedLine) {

        final String line = bufferedLine.toString();
        final int firstDelim = line.indexOf(',');
        final int lastDelim = line.lastIndexOf(',');
        if (firstDelim != -1 && lastDelim != -1 && firstDelim != lastDelim) {
            long timestamp = Long.parseLong(line.substring(0, firstDelim)) * 1000;
            double price = Double.parseDouble(line.substring(firstDelim + 1, lastDelim));
            this.dataset.put(timestamp, price);
        }
    }

    void enrichData(final Message<JsonArray> msg) {
        //array of
        /*
            [
                {target: "name", datapoints: [ [value,ts],[value,ts]] }
            ]
         */
        msg.reply(msg.body()
                     .stream()
                     .parallel()
                     .map(o -> (JsonObject) o)
                     .map(target -> new JsonObject().put("target", target.getString("target") + "_btc")
                                                    .put("datapoints",
                                                         adjustByBitcoingPrice(target.getJsonArray("datapoints")))

                     )
                     .collect(toJsonArray()));
    }

    private JsonArray adjustByBitcoingPrice(final JsonArray datapoints) {

        LOG.debug("Adjusting values for {}", datapoints.encode());
        JsonArray result = datapoints.stream()
                                     .parallel()
                                     .map(dp -> (JsonArray) dp)
                                     .map(dp -> new JsonArray().add(adjustByBitcoingPrice(dp.getLong(1),
                                                                                          dp.getDouble(0)))
                                                               .add(dp.getLong(1)))
                                     .sorted((a1, a2) -> a1.getLong(1).compareTo(a2.getLong(1)))
                                     .collect(toJsonArray());
        LOG.debug("Done adjusting {}", result.encode());
        return result;
    }

    /**
     * The method adjusts the original value by multiplying it with the bitcoin price at the same timestamp
     *
     * @param timestamp
     *         the timestamp to search for the right price
     * @param value
     *         the original value to be adjusted
     *
     * @return
     */
    private Double adjustByBitcoingPrice(final Long timestamp, final Double value) {

        double price = this.dataset.ceilingEntry(timestamp).getValue() / 1000;
        LOG.trace("Searching for {}, found {}", timestamp, price);
        return value * Math.pow(price, 10); //using pow to amplify visual effect
        //return value * price;
    }

}
