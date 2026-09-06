package spacecolony.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-rolled recursive-descent JSON parser. Returns a {@link JsonValue} tree. */
public final class JsonReader {
    private final String src;
    private int pos;

    private JsonReader(String src) { this.src = src; this.pos = 0; }

    public static JsonValue parse(String text) {
        JsonReader r = new JsonReader(text);
        r.skipWs();
        JsonValue v = r.parseValue();
        r.skipWs();
        if (r.pos != r.src.length()) throw r.err("trailing content");
        return v;
    }

    private JsonValue parseValue() {
        skipWs();
        if (pos >= src.length()) throw err("unexpected end of input");
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> new JsonValue.JsonString(parseString());
            case 't', 'f' -> parseBool();
            case 'n' -> parseNull();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
            default -> throw err("unexpected character '" + c + "'");
        };
    }

    private JsonValue.JsonObject parseObject() {
        expect('{');
        Map<String, JsonValue> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { pos++; return new JsonValue.JsonObject(map); }
        while (true) {
            skipWs();
            if (peek() != '"') throw err("expected string key");
            String key = parseString();
            skipWs();
            expect(':');
            JsonValue v = parseValue();
            map.put(key, v);
            skipWs();
            char next = peek();
            if (next == ',') { pos++; continue; }
            if (next == '}') { pos++; return new JsonValue.JsonObject(map); }
            throw err("expected ',' or '}'");
        }
    }

    private JsonValue.JsonArray parseArray() {
        expect('[');
        List<JsonValue> values = new ArrayList<>();
        skipWs();
        if (peek() == ']') { pos++; return new JsonValue.JsonArray(values); }
        while (true) {
            JsonValue v = parseValue();
            values.add(v);
            skipWs();
            char next = peek();
            if (next == ',') { pos++; continue; }
            if (next == ']') { pos++; return new JsonValue.JsonArray(values); }
            throw err("expected ',' or ']'");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) throw err("dangling escape");
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw err("bad unicode escape");
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw err("bad escape '\\" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
        throw err("unterminated string");
    }

    private JsonValue.JsonNumber parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && "0123456789.eE+-".indexOf(src.charAt(pos)) >= 0) pos++;
        String text = src.substring(start, pos);
        try {
            Double.parseDouble(text);  // validate
        } catch (NumberFormatException e) {
            throw err("invalid number '" + text + "'");
        }
        return new JsonValue.JsonNumber(text);
    }

    private JsonValue parseBool() {
        if (src.startsWith("true", pos))  { pos += 4; return new JsonValue.JsonBool(true); }
        if (src.startsWith("false", pos)) { pos += 5; return new JsonValue.JsonBool(false); }
        throw err("expected true/false");
    }

    private JsonValue parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; return new JsonValue.JsonNull(); }
        throw err("expected null");
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) throw err("expected '" + c + "'");
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw err("unexpected end of input");
        return src.charAt(pos);
    }

    private JsonParseException err(String msg) { return new JsonParseException(msg, pos); }
}
