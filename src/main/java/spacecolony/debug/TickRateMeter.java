package spacecolony.debug;

import java.util.ArrayDeque;
import java.util.Deque;

/** Measures real ticks per wall-clock second over a sliding window. */
public final class TickRateMeter {
    private static final long WINDOW_NANOS = 2_000_000_000L;

    /** (nanoTime, tick) pairs, oldest first. */
    private final Deque<long[]> marks = new ArrayDeque<>();

    public void record(long nowNanos, long tick) {
        marks.addLast(new long[] { nowNanos, tick });
        prune(nowNanos);
    }

    /** Ticks per second across the window ending at {@code nowNanos}; 0 when idle. */
    public double ticksPerSecond(long nowNanos) {
        prune(nowNanos);
        if (marks.size() < 2) return 0;
        long[] first = marks.peekFirst();
        long[] last = marks.peekLast();
        long dt = last[0] - first[0];
        if (dt <= 0) return 0;
        return (last[1] - first[1]) * 1e9 / dt;
    }

    public void reset() { marks.clear(); }

    private void prune(long nowNanos) {
        while (!marks.isEmpty() && nowNanos - marks.peekFirst()[0] > WINDOW_NANOS) marks.removeFirst();
    }
}
