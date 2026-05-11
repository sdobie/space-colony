package spacecolony.sim;

public record Event(
    long tick,
    EventSeverity severity,
    EventKind kind,
    String message,
    /** Optional context — null when not applicable. */
    String bodyId,
    String siteId,
    String shipId
) {
    public static Event info(long tick, EventKind kind, String message) {
        return new Event(tick, EventSeverity.INFO, kind, message, null, null, null);
    }
    public static Event warning(long tick, EventKind kind, String message) {
        return new Event(tick, EventSeverity.WARNING, kind, message, null, null, null);
    }
}
