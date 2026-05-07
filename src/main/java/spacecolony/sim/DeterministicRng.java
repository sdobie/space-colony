package spacecolony.sim;

import java.util.Random;

/**
 * Constructs a {@link Random} keyed off (seed, tick, stepId) for reproducibility.
 * Mixing function uses splittable-style avalanche so neighboring (tick, stepId) pairs
 * yield uncorrelated streams.
 */
public final class DeterministicRng {
    private DeterministicRng() {}

    public static Random forStep(long seed, long tick, long stepId) {
        long h = seed;
        h = mix(h ^ tick);
        h = mix(h ^ stepId);
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
