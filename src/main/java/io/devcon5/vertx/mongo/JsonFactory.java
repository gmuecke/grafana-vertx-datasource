package io.devcon5.vertx.mongo;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 *
 */
public final class JsonFactory {

    private JsonFactory(){}

    /**
     * Creates an empty JsonArray
     * @return
     */
    public static JsonArray arr() {
        return new JsonArray();
    }

    /**
     * Creates a JsonArray parse the specified json array string
     * @param json
     *  a string describing a json array
     * @return
     *
     */
    public static JsonArray arr(String json) {
        return new JsonArray(json);
    }

    /**
     * Creates a JsonArray containing the specified elements
     * @param o
     *  the objects or values to be contained in the array
     * @return
     */
    public static JsonArray arr(Object... o) {
        return Arrays.stream(o).collect(toJsonArray());
    }

    /**
     * Creates an empty Json Object
     * @return
     */
    public static JsonObject obj() {
        return new JsonObject();
    }

    /**
     * Creates a JsonObject with a single key-value pair
     * @param name
     *  the name of the first key
     * @param value
     *  the value of the first key
     * @return
     */
    public static JsonObject obj(String name, Object value) {
        return new JsonObject().put(name, value);
    }

    /**
     * Creates a JsonObject parse the specified json string
     * @param name
     *  a string representation of the object
     * @return
     */
    public static JsonObject obj(String name) {
        return new JsonObject(name);
    }

    /**
     * Converts the field values of on object into an array
     *
     * @param o
     *         source object whose fields should be collected
     * @param fieldMapper
     *         mapper to map all selected fields to the same type
     * @param fieldNames
     *         name of the fields to be mapped
     *
     * @return a json array containing the field values
     */
    public static JsonArray fieldsToArray(JsonObject o, BiFunction<JsonObject, String, ?> fieldMapper, String... fieldNames) {
        return Stream.of(fieldNames).filter(o::containsKey).map(f -> fieldMapper.apply(o, f)).collect(toJsonArray());
    }

    /**
     * Creates a collector for collecting elements (strings, objects etc) into an array.
     * @param <T>
     *      type of the contents of the array
     * @return
     *  a collector to be used as terminal operation in streams
     */
    public static <T> Collector<T, JsonArray, JsonArray> toJsonArray() {
        return Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }
}
