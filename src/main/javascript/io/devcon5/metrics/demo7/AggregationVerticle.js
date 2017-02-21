var config = Vertx.currentContext().config();

/**
 * Verticle to build the aggregation pipeline for mongo db. The aggregation pipeline is an array
 * of aggregation steps. Some of those steps are the same for all processings, some are unique.
 */


/**
 * Default steps for building the aggregation pipeline.
 * The pipeline should have a first match step that applies the filtering for the data.
 * Further limiting and sorting are applied after a compaction.
 * The compaction is applied to aggegate multiple datavalues into single values with the requested
 * resolution by applying an aggegation function.
 * @type {{match: steps.match, limit: steps.limit, sort: steps.sort, compaction: steps.compaction}}
 */
var steps = {

    match: function (q) {

        var from = {};
        var to = {};
        from[q.tsField] = {"$gte": q.range.from};
        to[q.tsField] = {"$lte": q.range.to};
        console.log(JSON.stringify(q.range));
        console.log(JSON.stringify(from));
        console.log(JSON.stringify(to));

        return {
            "$match": {
                "$and": [
                    q.query,
                    from,
                    to
                ]
            }
        };
    },
    aggegate: function (aggregation) {
        return {
            "$group": aggregation
        }
    },
    limit: function (q) {
        return {"$limit": q.maxDataPoints}
    },
    sort: function (field) {
        var fields = {};
        fields[field] = 1;
        return {"$sort": fields};
    },

    /**
     * Defines a default compaction for the given query document. The compaction creates an identifier field with
     * the name as defined by nameMap and an interval index depending on the value of the timestamp field (tsField) and
     * the currently selected range and interval.
     * @param q
     * @param aggegation
     *  a document to be applied as aggegation function mapping to the "value" property
     * @returns {{_id: {name: string, interval: {$trunc: {$divide: *[]}}, ts: {$add: *[]}}}}
     */
    compaction: function (q, aggegation) {
        return {
            "_id": {
                "name": "$" + q.nameMap,
                "interval": {"$trunc": {"$divide": [{"$subtract": ["$" + q.tsField, q.range.from]}, q.interval]}},
                "ts": {"$add": [q.range.from, {"$multiply": [q.interval, {"$trunc": {"$divide": [{"$subtract": ["$" + q.tsField, q.range.from]}, q.interval]}}]}]}
            },
            "value": aggegation
        }
    }

};


/**
 * All supported aggregations
 * @type {{avg: aggregations.avg, sum: aggregations.sum, count: aggregations.count, min: aggregations.min, max:
 *     aggregations.max}}
 */
var aggregations = {

    avg: {
        params: {
            fieldName: ""
        },
        apply: function (query) {
            return [
                steps.match(query),
                steps.aggegate(steps.compaction(query, {"$avg": "$" + query.valueMap})),
                steps.limit(query),
                steps.sort("_id.interval"),
            ];
        }
    },
    sum: {
        params: {
            fieldName: ""
        },
        apply: function (query) {
            return [
                steps.match(query),
                steps.aggegate(steps.compaction(query, {"$sum": "$" + query.valueMap})),
                steps.limit(query),
                steps.sort("_id.interval"),
            ];
        }
    },
    count: {
        params: {},
        apply: function (query) {
            return [
                steps.match(query),
                steps.aggegate(steps.compaction(query, {"$sum": 1})),
                steps.limit(query),
                steps.sort("_id.interval"),
            ];
        }
    },
    min: {
        params: {
            fieldName: ""
        },
        apply: function (query) {
            return [
                steps.match(query),
                steps.aggegate(steps.compaction(query, {"$min": "$" + query.valueMap})),
                steps.limit(query),
                steps.sort("_id.interval")
            ];
        }
    },
    max: {
        params: {
            fieldName: ""
        },
        apply: function (query) {
            return [
                steps.match(query),
                steps.aggegate(steps.compaction(query, {"$max": "$" + query.valueMap})),
                steps.limit(query),
                steps.sort("_id.interval"),
            ];
        }
    },
    pcl: {
        params: {
            fieldName : "",
            percentile: 0.90
        },
        apply: function (query) {
            return [
                steps.match(query),
                steps.sort("n.value"),
                steps.aggegate(steps.compaction(query, {"$push": "$n.value"})),
                steps.limit(query),
                steps.sort("_id.ts"),
                {
                    "$project": {
                        "_id": 1,
                        "value": {"$arrayElemAt": ["$value", {"$floor": {'$multiply': [query.params.percentile, {'$size': '$value'}]}}]},
                    }
                }
            ];
        }
    }
};


/**
 * Deliver aggregation options provided by this verticle
 */
vertx.eventBus().consumer("/options/aggregations", function (msg) {

    var aggOpts = [];
    for (var fun in aggregations) {
        if (aggregations.hasOwnProperty(fun)) {

            var f = {
                name: fun,
                params: aggregations[fun].params
            };
            aggOpts.push(f)
        }
    }
    msg.reply(aggOpts)

});

/**
 * Builds the pipeline based on the selected aggegation
 */
vertx.eventBus().consumer("build::aggregationPipeline", function (msg) {
    var q = msg.body();
    var pipeline = aggregations[q.aggregation].apply(q);
    msg.reply(pipeline);
});
