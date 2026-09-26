package spacecolony.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.Simulator;

/** Rolling window of per-phase simulator timings for the debug overlay (last 50 ticks). */
public final class PhaseTimings implements Simulator.PhaseObserver {
    public static final int WINDOW = 50;

    private final Deque<long[]> samples = new ArrayDeque<>();

    @Override public void onTick(long tick, long[] phaseNanos) {
        samples.addLast(phaseNanos.clone());
        while (samples.size() > WINDOW) samples.removeFirst();
    }

    public int sampleCount() { return samples.size(); }

    /** Mean nanos per phase over the window; zeros when empty. */
    public long[] averageNanos() {
        long[] sum = new long[Simulator.PHASE_NAMES.length];
        for (long[] s : samples) for (int i = 0; i < sum.length; i++) sum[i] += s[i];
        if (!samples.isEmpty()) for (int i = 0; i < sum.length; i++) sum[i] /= samples.size();
        return sum;
    }

    /** Worst nanos per phase over the window; zeros when empty. */
    public long[] maxNanos() {
        long[] max = new long[Simulator.PHASE_NAMES.length];
        for (long[] s : samples) for (int i = 0; i < max.length; i++) max[i] = Math.max(max[i], s[i]);
        return max;
    }

    /** Total nanos of the most recent tick, or 0 before any tick. */
    public long lastTotalNanos() {
        long[] last = samples.peekLast();
        if (last == null) return 0;
        long t = 0;
        for (long v : last) t += v;
        return t;
    }

    public void clear() { samples.clear(); }
}
