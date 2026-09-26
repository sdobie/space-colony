package spacecolony.debug;

import java.util.EnumMap;
import java.util.Map;
import spacecolony.sim.PhaseObserver;
import spacecolony.sim.SimPhase;

/** Rolling window of per-phase simulator timings for the overlay. EDT-only, like the engine. */
public final class PhaseTimings implements PhaseObserver {
    public record Stat(int samples, double meanNanos, long maxNanos) {}

    private final int window;
    private final Map<SimPhase, long[]> rings = new EnumMap<>(SimPhase.class);
    private final Map<SimPhase, Integer> counts = new EnumMap<>(SimPhase.class);

    public PhaseTimings(int window) {
        this.window = window;
        for (SimPhase p : SimPhase.values()) {
            rings.put(p, new long[window]);
            counts.put(p, 0);
        }
    }

    @Override public void phaseDone(long tick, SimPhase phase, long nanos) {
        int c = counts.get(phase);
        rings.get(phase)[c % window] = nanos;
        counts.put(phase, c + 1);
    }

    public Stat stat(SimPhase p) {
        int n = Math.min(counts.get(p), window);
        if (n == 0) return new Stat(0, 0, 0);
        long[] ring = rings.get(p);
        long sum = 0, max = 0;
        for (int i = 0; i < n; i++) {
            sum += ring[i];
            max = Math.max(max, ring[i]);
        }
        return new Stat(n, (double) sum / n, max);
    }
}
