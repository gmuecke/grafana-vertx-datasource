package io.devcon5.vertx.mongo;

import static io.devcon5.vertx.mongo.JsonFactory.arr;
import static io.devcon5.vertx.mongo.JsonFactory.obj;

import io.vertx.core.json.JsonObject;

/**
 *
 */
public class Aggregation {

    //ARITHMETIC
    public static JsonObject $add(Object... expression){
        return obj("$add", arr(expression));
    }
    public static JsonObject $subtract(Object... expression){
        return obj("$subtract", arr(expression));
    }
    public static JsonObject $divide(Object... expression){
        return obj("$divide", arr(expression));
    }
    public static JsonObject $multiply(Object... expression){
        return obj("$multiply", arr(expression));
    }
    public static JsonObject $mod(Object... expression){
        return obj("$mod", arr(expression));
    }
    public static JsonObject $trunc(Object expression){
        return obj("$trunc", expression);
    }
    //AGGREGATIOn
    public static JsonObject $sum(Object expression){
        return obj("$sum", expression);
    }
    public static JsonObject $avg(Object expression){
        return obj("$avg", expression);
    }
    public static JsonObject $min(Object expression){
        return obj("$min", expression);
    }
    public static JsonObject $max(Object expression){
        return obj("$max", expression);
    }
    //STAGES
    public static JsonObject $match(JsonObject filter){
        return obj("$match", filter);
    }
    public static JsonObject $sort(JsonObject filter){
        return obj("$sort", filter);
    }
    public static JsonObject $group(JsonObject filter){
        return obj("$group", filter);
    }
    public static JsonObject $project(JsonObject filter){
        return obj("$project", filter);
    }
    public static JsonObject $limit(int maxEntries){
        return obj("$limit", maxEntries);
    }

}
