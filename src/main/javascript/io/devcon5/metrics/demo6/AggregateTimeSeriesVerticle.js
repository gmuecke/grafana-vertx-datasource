var RangeParser = Java.type("io.devcon5.metrics.util.RangeParser");
var IntervalParser = Java.type("io.devcon5.metrics.util.IntervalParser");
var rangeParser = new RangeParser();
var intervalParser = new IntervalParser();

//get the config for this verticle
var config = Vertx.currentContext().config();

////// Address for the EventBus
var address = config.address;
if (address == null) {
    address = "/query";
}
////// Mongo Initialization
var MongoClient = require("vertx-mongo-js/mongo_client");
var mongoconfig = config.mongo;
var mongoClient = MongoClient.createShared(vertx, mongoconfig);
var collection = mongoconfig.col_name;



vertx.eventBus().consumer(address, function (msg) {

    var query = msg.body();

    var range = rangeParser.parse(query.range.from, query.range.to);
    var interval = intervalParser.parseToLong(query.interval);
    var maxDataPoints = query.maxDataPoints;
    var targets = [];
    query.targets.forEach(function (o) {
        targets.push(o.target);
    });

    //build the command with the aggegation pipelnine
    var cmd = {
        "aggregate": collection,
        "pipeline": [
            {
                "$match": {
                    "$and": [
                        {"t.name": {"$in": targets}},
                        {"n.begin": {"$gte": range.start}},
                        {"n.begin": {"$lte": range.end}}
                    ]
                }
            },
            {
                "$group": {
                    "_id": {
                        "name": "$t.name",
                        "interval": {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}},
                        "ts": {"$add": [range.start, {"$multiply": [interval, {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}}]}]}
                    },
                    "count": {"$sum": 1},
                    "sum": {"$sum": "$n.value"},
                    "avg": {"$avg": "$n.value"},
                    "min": {"$min": "$n.value"},
                    "max": {"$max": "$n.value"}
                }
            },
            {"$limit": maxDataPoints},
            {
                "$sort": {"_id.interval": 1}
            }
        ]
    };


    mongoClient.runCommand("aggregate", cmd, function (res, res_err) {

        if (res_err == null) {
            var result = res.result;
            var response = [];
            for (var i = 0; i < targets.length; i++) {
                var datapoints = [];
                for (var j = 0; j < result.length; j++) {
                    var rawDataPoint = result[j];
                    if (targets[i] === rawDataPoint._id.name) {
                        datapoints.push([rawDataPoint.avg, rawDataPoint._id.ts])
                    }
                }
                response.push({"target": targets[i], "datapoints": datapoints});
            }
            msg.reply(response);
        } else {
            console.error("Annotation query failed" + res_err);
            msg.reply([]);
        }
    });

});

