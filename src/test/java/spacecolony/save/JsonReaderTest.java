package spacecolony.save;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

    @Test
    void parsesNull() {
        assertTrue(JsonReader.parse("null") instanceof JsonValue.JsonNull);
    }

    @Test
    void parsesBoolean() {
        assertEquals(true,  ((JsonValue.JsonBool) JsonReader.parse("true")).value());
        assertEquals(false, ((JsonValue.JsonBool) JsonReader.parse("false")).value());
    }

    @Test
    void parsesInteger() {
        JsonValue.JsonNumber n = (JsonValue.JsonNumber) JsonReader.parse("42");
        assertEquals(42L, n.asLong());
    }

    @Test
    void parsesNegativeAndDecimal() {
        assertEquals(-3.14, ((JsonValue.JsonNumber) JsonReader.parse("-3.14")).asDouble(), 1e-9);
        assertEquals(0L,    ((JsonValue.JsonNumber) JsonReader.parse("0")).asLong());
    }

    @Test
    void parsesString_withEscapes() {
        assertEquals("hello",       ((JsonValue.JsonString) JsonReader.parse("\"hello\"")).value());
        assertEquals("a\"b\\c",     ((JsonValue.JsonString) JsonReader.parse("\"a\\\"b\\\\c\"")).value());
        assertEquals("line\nbreak", ((JsonValue.JsonString) JsonReader.parse("\"line\\nbreak\"")).value());
        assertEquals("é",      ((JsonValue.JsonString) JsonReader.parse("\"\\u00e9\"")).value());
    }

    @Test
    void parsesArray() {
        JsonValue.JsonArray a = (JsonValue.JsonArray) JsonReader.parse("[1, 2, 3]");
        assertEquals(3, a.values().size());
        assertEquals(2L, ((JsonValue.JsonNumber) a.values().get(1)).asLong());
    }

    @Test
    void parsesObject() {
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(
            "{\"name\": \"Earth\", \"tick\": 42}");
        assertEquals("Earth", ((JsonValue.JsonString) o.values().get("name")).value());
        assertEquals(42L,     ((JsonValue.JsonNumber) o.values().get("tick")).asLong());
    }

    @Test
    void parsesNestedStructure() {
        String json = "{\"a\":[1,{\"b\":true}],\"c\":null}";
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(json);
        JsonValue.JsonArray a = (JsonValue.JsonArray) o.values().get("a");
        JsonValue.JsonObject inner = (JsonValue.JsonObject) a.values().get(1);
        assertEquals(true, ((JsonValue.JsonBool) inner.values().get("b")).value());
        assertTrue(o.values().get("c") instanceof JsonValue.JsonNull);
    }

    @Test
    void parsesEmptyArrayAndObject() {
        assertEquals(List.of(), ((JsonValue.JsonArray) JsonReader.parse("[]")).values());
        assertTrue(((JsonValue.JsonObject) JsonReader.parse("{}")).values().isEmpty());
    }

    @Test
    void tolerates_whitespace() {
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(
            "  {\n  \"a\" : 1 ,\n  \"b\" : 2\n}  ");
        assertEquals(2, o.values().size());
    }

    @Test
    void throwsOnMalformedInput() {
        assertThrows(JsonParseException.class, () -> JsonReader.parse("{"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("{\"a\":}"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("[1, 2,]"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("not json"));
    }
}
