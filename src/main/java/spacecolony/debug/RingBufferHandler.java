package spacecolony.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Keeps the last {@code capacity} log records in memory for the debug log viewer and the
 * overlay's recent-exceptions line. Thread-safe: records can arrive from any thread.
 */
public final class RingBufferHandler extends Handler {
    private final int capacity;
    private final Deque<LogRecord> records = new ArrayDeque<>();
    /** Bumped on every publish so viewers can cheaply tell whether anything is new. */
    private long sequence;

    public RingBufferHandler(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    @Override public synchronized void publish(LogRecord r) {
        if (r == null || !isLoggable(r)) return;
        records.addLast(r);
        while (records.size() > capacity) records.removeFirst();
        sequence++;
    }

    /** Oldest-first copy of the buffered records. */
    public synchronized List<LogRecord> snapshot() { return new ArrayList<>(records); }

    public synchronized long sequence() { return sequence; }

    public synchronized void clear() {
        records.clear();
        sequence++;
    }

    public int capacity() { return capacity; }

    @Override public void flush() {}
    @Override public void close() {}
}
