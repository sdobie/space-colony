package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * A planet, moon, or asteroid. Body position at any tick is computed from {@link #orbit};
 * it is not stored. Resource yields are baked at world-gen time as a 2D grid sampled when
 * sites are placed; the grid lives on this Body. Surface seed is consumed by the renderer
 * (Plan 2) and by anything that wants per-body deterministic randomness.
 */
public class Body {
    public final String id;
    public final String name;
    public final BodyType type;
    public final Orbit orbit;
    public final double mass;
    public final double radius;
    public final long surfaceSeed;
    /** Resource yield multipliers; null on body construction is allowed (set during world-gen). */
    public final ResourceYieldSampler resourceYields;
    public final List<Site> sites = new ArrayList<>();

    public Body(String id, String name, BodyType type, Orbit orbit,
                double mass, double radius, long surfaceSeed, ResourceYieldSampler resourceYields) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.orbit = orbit;
        this.mass = mass;
        this.radius = radius;
        this.surfaceSeed = surfaceSeed;
        this.resourceYields = resourceYields;
    }
}
