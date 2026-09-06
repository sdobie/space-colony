package spacecolony.save;

import java.util.ArrayList;
import java.util.List;

/** Pretty-prints {@link JsonValue} trees with sorted keys and 2-space indent. */
public final class JsonWriter {
    private JsonWriter() {}

    public static String write(JsonValue v) {
        StringBuilder sb = new StringBuilder();
        append(sb, v, 0);
        return sb.toString();
    }

    private static void append(StringBuilder sb, JsonValue v, int indent) {
        switch (v) {
            case JsonValue.JsonNull ignored -> sb.append("null");
            case JsonValue.JsonBool b -> sb.append(b.value() ? "true" : "false");
            case JsonValue.JsonNumber n -> sb.append(n.text());
            case JsonValue.JsonString s -> appendString(sb, s.value());
            case JsonValue.JsonArray a -> appendArray(sb, a, indent);
            case JsonValue.JsonObject o -> appendObject(sb, o, indent);
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static void appendArray(StringBuilder sb, JsonValue.JsonArray a, int indent) {
        if (a.values().isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        for (int i = 0; i < a.values().size(); i++) {
            sb.append('\n');
            indent(sb, indent + 1);
            append(sb, a.values().get(i), indent + 1);
            if (i < a.values().size() - 1) sb.append(',');
        }
        sb.append('\n');
        indent(sb, indent);
        sb.append(']');
    }

    private static void appendObject(StringBuilder sb, JsonValue.JsonObject o, int indent) {
        if (o.values().isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        List<String> keys = new ArrayList<>(o.values().keySet());
        keys.sort(String::compareTo);
        for (int i = 0; i < keys.size(); i++) {
            sb.append('\n');
            indent(sb, indent + 1);
            appendString(sb, keys.get(i));
            sb.append(": ");
            append(sb, o.values().get(keys.get(i)), indent + 1);
            if (i < keys.size() - 1) sb.append(',');
        }
        sb.append('\n');
        indent(sb, indent);
        sb.append('}');
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }
}
