package spacecolony.sim;

import java.util.Random;

/**
 * Constructs a {@link Random} keyed off (seed, tick, stepId) for reproducibility.
 * Mixing function uses splittable-style avalanche so neighboring (tick, stepId) pairs
 * yield uncorrelated streams.
 */
public final class DeterministicRng {
    private DeterministicRng() {}

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    public static Random forStep(long seed, long tick, long stepId) {
        long h = mix(seed);
        h = mix(h + GOLDEN + tick);
        h = mix(h + GOLDEN + stepId);
        return new Random(h);
    }

    private static long mix(long x) {
        x ^= (x >>> 30);
        x *= 0xbf58476d1ce4e5b9L;
        x ^= (x >>> 27);
        x *= 0x94d049bb133111ebL;
        x ^= (x >>> 31);
        return x;
    }
}
