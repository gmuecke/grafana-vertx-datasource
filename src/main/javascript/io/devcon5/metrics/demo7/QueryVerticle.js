var MongoClient = require("vertx-mongo-js/mongo_client");
var config = Vertx.currentContext().config();
var dbname = config.mongo.db_name;
var mongo = MongoClient.createShared(vertx, config.mongo);


//get the config for this verticle
var config = Vertx.currentContext().config();

////// Address for the EventBus
var address = config.address;
if (address == null) {
    address = "/queryTarget";
}

vertx.eventBus().consumer("/" + dbname + address, function (msg) {

    var target = msg.body();

    var range = target.range;
    var interval = target.interval;
    var maxDataPoints = target.maxDataPoints;
    var query = target.query;

    vertx.eventBus().send("build::aggregationPipeline", {
            query: query.mongoQuery,
            range: range,
            interval: interval,
            tsField: query.tsField,
            maxDataPoints: maxDataPoints,
            nameMap: query.labelField,
            valueMap: query.aggregation.field,
            aggregation: query.aggregation.fun.name,
            params : query.aggregation.fun.params
        }
        , function (reply) {

            var cmd = {
                "aggregate": query.collection,
                "pipeline": reply.body(),
                "allowDiskUse" : true
            };
            console.log(JSON.stringify(cmd));

            mongo.runCommand("aggregate", cmd, function (res, res_err) {

                if (res_err == null) {
                    var result = res.result;
                    console.log(JSON.stringify(result));
                    var datapoints = [];
                    var label = "unknown";
                    for (var j = 0; j < result.length; j++) {
                        var rawDataPoint = result[j];
                        datapoints.push([rawDataPoint.value, rawDataPoint._id.ts]);

                        //TODO this is hacky
                        if (label == "unknown") {
                            label = rawDataPoint._id.name;
                        }
                    }
                    msg.reply({"target": label, "datapoints": datapoints});
                } else {
                    console.error("Annotation query failed" + res_err);
                    msg.reply([]);
                }
            });
        });
});

