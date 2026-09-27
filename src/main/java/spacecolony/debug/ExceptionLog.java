package spacecolony.debug;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

/** Bounded, thread-safe list of recent uncaught exceptions for the overlay banner. */
public final class ExceptionLog {
    public record Entry(Instant at, String thread, Throwable error) {}

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final int capacity;
    private long total;

    public ExceptionLog(int capacity) { this.capacity = capacity; }

    public synchronized void add(Thread t, Throwable e) {
        entries.addLast(new Entry(Instant.now(), t.getName(), e));
        while (entries.size() > capacity) entries.removeFirst();
        total++;
    }

    public synchronized List<Entry> snapshot() { return List.copyOf(entries); }

    /** Exceptions seen since startup, including ones evicted from the list. */
    public synchronized long total() { return total; }

    public synchronized void clear() { entries.clear(); }
}
