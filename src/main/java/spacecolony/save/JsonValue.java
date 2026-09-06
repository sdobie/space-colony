package spacecolony.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sealed tree of JSON values returned by {@link JsonReader#parse(String)}. */
public sealed interface JsonValue
    permits JsonValue.JsonObject,
            JsonValue.JsonArray,
            JsonValue.JsonString,
            JsonValue.JsonNumber,
            JsonValue.JsonBool,
            JsonValue.JsonNull {

    record JsonObject(Map<String, JsonValue> values) implements JsonValue {
        public JsonObject() { this(new LinkedHashMap<>()); }
    }
    record JsonArray(List<JsonValue> values) implements JsonValue {}
    record JsonString(String value) implements JsonValue {}
    /** Number is stored as a string for lossless representation; convert via {@link #asLong} or {@link #asDouble}. */
    record JsonNumber(String text) implements JsonValue {
        public long asLong() { return Long.parseLong(text); }
        public double asDouble() { return Double.parseDouble(text); }
    }
    record JsonBool(boolean value) implements JsonValue {}
    record JsonNull() implements JsonValue {}
}
