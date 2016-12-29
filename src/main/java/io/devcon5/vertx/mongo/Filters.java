package io.devcon5.vertx.mongo;

import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public class Filters {

    public static $AND $and(JsonObject... operands) {
        return new $AND().add(operands);
    }

    public static $AND $and(JsonArray operands) {
        return new $AND().add(operands);
    }
    public static $OR $or(JsonObject... operands) {
        return new $OR().add(operands);
    }

    public static $OR $or(JsonArray operands) {
        return new $OR().add(operands);
    }

    public static JsonObject $in(JsonArray list) {

        return new JsonObject().put("$in", list);
    }

    public static JsonObject $gte(Object o) {

        return new JsonObject().put("$gte", o);
    }

    public static JsonObject $gt(Object o) {

        return new JsonObject().put("$gt", o);
    }

    public static JsonObject $lte(Object o) {

        return new JsonObject().put("$lte", o);
    }

    public static JsonObject $lt(Object o) {

        return new JsonObject().put("$lte", o);
    }

    /**
     * Helper class to conveniently add additional operands
     */
    public static class $AND extends LogicalOperand<$AND> {

        $AND() {

            super("$and");
        }
    }

    public static class $OR extends LogicalOperand<$OR> {

        $OR() {

            super("$or");
        }
    }

    public static class LogicalOperand<T> extends JsonObject {

        private final String operandName;

        LogicalOperand(final String operandName) {
            super();
            this.operandName = operandName;
            put(operandName, JsonFactory.arr());
        }

        public T add(JsonObject... operands) {
            getJsonArray(operandName).addAll(Stream.of(operands).collect(JsonFactory.toJsonArray()));
            return (T) this;
        }
        public T add(JsonArray operands) {
            getJsonArray(operandName).addAll(operands);
            return (T) this;
        }
    }
}
