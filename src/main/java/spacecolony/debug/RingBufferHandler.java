package spacecolony.debug;

import java.util.ArrayDeque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Keeps the newest {@code capacity} LogRecords in memory for the log viewer. Thread-safe. */
public final class RingBufferHandler extends Handler {
    private final ArrayDeque<LogRecord> records = new ArrayDeque<>();
    private final int capacity;
    private long version;

    public RingBufferHandler(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        setLevel(Level.ALL);
    }

    @Override public synchronized void publish(LogRecord r) {
        if (r == null || !isLoggable(r)) return;
        records.addLast(r);
        while (records.size() > capacity) records.removeFirst();
        version++;
    }

    /** Oldest-first copy of the buffered records. */
    public synchronized List<LogRecord> snapshot() { return List.copyOf(records); }

    /** Monotonic counter; the viewer polls it to skip redundant refreshes. */
    public synchronized long version() { return version; }

    public synchronized void clear() {
        records.clear();
        version++;
    }

    @Override public void flush() {}
    @Override public void close() {}
}
