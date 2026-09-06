package spacecolony.save;

/** Thrown by {@link JsonReader#parse(String)} on malformed input. Unchecked. */
public class JsonParseException extends RuntimeException {
    public final int position;
    public JsonParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }
}
