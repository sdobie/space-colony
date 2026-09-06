package spacecolony.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonWriterTest {

    @Test
    void writesPrimitives() {
        assertEquals("null",   JsonWriter.write(new JsonValue.JsonNull()));
        assertEquals("true",   JsonWriter.write(new JsonValue.JsonBool(true)));
        assertEquals("false",  JsonWriter.write(new JsonValue.JsonBool(false)));
        assertEquals("42",     JsonWriter.write(new JsonValue.JsonNumber("42")));
        assertEquals("\"hi\"", JsonWriter.write(new JsonValue.JsonString("hi")));
    }

    @Test
    void escapesSpecialCharsInStrings() {
        String out = JsonWriter.write(new JsonValue.JsonString("a\"b\\c\nd"));
        assertEquals("\"a\\\"b\\\\c\\nd\"", out);
    }

    @Test
    void writesEmptyArrayAndObject() {
        assertEquals("[]", JsonWriter.write(new JsonValue.JsonArray(List.of())));
        assertEquals("{}", JsonWriter.write(new JsonValue.JsonObject(new LinkedHashMap<>())));
    }

    @Test
    void sortsObjectKeysAlphabetically() {
        Map<String, JsonValue> m = new LinkedHashMap<>();
        m.put("zebra", new JsonValue.JsonNumber("1"));
        m.put("apple", new JsonValue.JsonNumber("2"));
        String out = JsonWriter.write(new JsonValue.JsonObject(m));
        assertTrue(out.indexOf("\"apple\"") < out.indexOf("\"zebra\""),
            "apple must precede zebra in output: " + out);
    }

    @Test
    void prettyPrintsNestedStructure() {
        Map<String, JsonValue> m = new LinkedHashMap<>();
        m.put("a", new JsonValue.JsonArray(List.of(
            new JsonValue.JsonNumber("1"), new JsonValue.JsonNumber("2"))));
        m.put("b", new JsonValue.JsonString("hi"));
        String out = JsonWriter.write(new JsonValue.JsonObject(m));
        // Pretty-printed: contains newlines and 2-space indent.
        assertTrue(out.contains("\n"));
        assertTrue(out.contains("  \"a\""));
    }

    @Test
    void roundTrip_preservesStructure() {
        String original = "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":-2.5}}";
        JsonValue tree = JsonReader.parse(original);
        String written = JsonWriter.write(tree);
        JsonValue reparsed = JsonReader.parse(written);
        assertEquals(JsonWriter.write(tree), JsonWriter.write(reparsed),
            "round-trip should be idempotent after first parse+write");
    }
}
