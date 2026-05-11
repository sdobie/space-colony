package spacecolony.sim;

/**
 * Samples per-resource yield at a (lat, lon) on a body. Implementations live outside
 * the sim package (see {@code spacecolony.world.ResourceYieldMap}); declaring the
 * interface here lets the simulator consume yields without importing the world package,
 * preserving the one-way {@code world → sim} layering.
 */
public interface ResourceYieldSampler {
    /**
     * @param r resource to sample
     * @param lat radians in {@code [-π/2, π/2]}
     * @param lon radians in {@code [-π, π]}
     * @return yield multiplier in {@code [0, 1]}; 0 means absent, 1 means rich
     */
    double sample(Resource r, double lat, double lon);
}
