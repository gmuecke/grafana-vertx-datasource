var RangeParser = Java.type("io.devcon5.metrics.util.RangeParser");
var IntervalParser = Java.type("io.devcon5.metrics.util.IntervalParser");
var rangeParser = new RangeParser();
var intervalParser = new IntervalParser();

//get the config for this verticle
var config = Vertx.currentContext().config();

////// Address for this Verticle on the EventBus
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
    //make the interval larger for lower density timeseries
    interval *= 16;
    var maxDataPoints = query.maxDataPoints;
    var targets = [];
    query.targets.forEach(function (o) {
        targets.push(o.target);
    });

    //build the command with the aggegation pipeline
    //the pipeline is according to "how to calculate percentiles"
    // http://www.dummies.com/education/math/statistics/how-to-calculate-percentiles-in-statistics/
    var cmd = {
        "aggregate": collection,
        "allowDiskUse": true,
        "pipeline": [
            {
                "$match": {
                    "$and": [
                        {"t.name": {"$in": targets}},
                        {"n.begin": {"$gte": range.start}},
                        {"n.begin": {"$lte": range.end}}
                    ]
                }
            }, {
                "$sort": {"n.value": 1}
            }, {
                "$group": {
                    "_id": {
                        "name": "$t.name",
                        "interval": {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}},
                        "ts": {"$add": [range.start, {"$multiply": [interval, {"$trunc": {"$divide": [{"$subtract": ["$n.begin", range.start]}, interval]}}]}]}
                    },
                    "value": {"$push": "$n.value"}
                }
            }, {
                "$limit": maxDataPoints
            }, {
                "$sort" : {"_id.ts" : 1}
            },{
                "$project": {
                    "_id": 1,
                    "top50": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [0.50, {'$size': '$value'}]}}]},
                    "top80": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [0.80, {'$size': '$value'}]}}]},
                    "top90": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [0.90, {'$size': '$value'}]}}]},
                    "top95": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [0.95, {'$size': '$value'}]}}]},
                    "top99": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [0.99, {'$size': '$value'}]}}]}
                }
            }
        ]
    };


    mongoClient.runCommand("aggregate", cmd, function (res, res_err) {

        if (res_err == null) {
            var result = res.result;
            var response = [];
            targets.forEach(function (target) {
                ["top50", "top80", "top90", "top95", "top99"].forEach(function (stat) {
                    var label = target + "_" + stat;
                    var datapoints = [];
                    result.forEach(function (rawDataPoint) {
                        if (target === rawDataPoint._id.name) {
                            datapoints.push([rawDataPoint[stat], rawDataPoint._id.ts])
                        }
                    });
                    response.push({"target": label, "datapoints": datapoints});
                });
            });

            msg.reply(response);
        }
        else {
            console.error("Annotation query failed" + res_err);
            msg.reply([]);
        }
    });

})
;

