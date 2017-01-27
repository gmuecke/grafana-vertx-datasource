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

    var query = msg.body();

    var range = query.range;
    var interval = query.interval;
    var maxDataPoints = query.maxDataPoints;
    var target = query.target;

    var tsField = query.tsField;
    var from = {};
    var to = {};
    console.log(JSON.stringify(range));
    from[tsField] = {"$gte": range.from};
    to[tsField] = {"$lte": range.to};
    console.log(JSON.stringify(from));
    console.log(JSON.stringify(to));

    //build the command with the aggegation pipelnine
    var cmd = {
        "aggregate": target.collection,
        "pipeline": [
            {
                "$match": {
                    "$and": [
                        target.query,
                        from,
                        to
                    ]
                }
            },
            {
                "$group": {
                    "_id": {
                        //TODO define name mapping in client
                        "name": "$t.name",
                        "interval": {"$trunc": {"$divide": [{"$subtract": ["$" + tsField, range.from]}, interval]}},
                        "ts": {"$add": [range.from, {"$multiply": [interval, {"$trunc": {"$divide": [{"$subtract": ["$" + tsField, range.from]}, interval]}}]}]}
                    },
                    "count": {"$sum": 1},
                    //TODO define value mapping in client
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

    console.log(JSON.stringify(cmd));

    mongo.runCommand("aggregate", cmd, function (res, res_err) {

        if (res_err == null) {
            var result = res.result;
            var datapoints = [];
            console.log(JSON.stringify(result));
            for (var j = 0; j < result.length; j++) {
                var rawDataPoint = result[j];
                datapoints.push([rawDataPoint[target.aggregation], rawDataPoint._id.ts])
            }
            msg.reply({"target": target, "datapoints": datapoints});
        } else {
            console.error("Annotation query failed" + res_err);
            msg.reply([]);
        }
    });

});

