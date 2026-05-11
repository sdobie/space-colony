package spacecolony.world;

import java.util.List;
import spacecolony.sim.BodyType;

/**
 * The fixed hand-tuned solar system layout. Same for every game.
 * Tick = 1 day, so periods are in days. Earth period = 365 days.
 */
public final class SystemLayout {
    private SystemLayout() {}

    public record BodySpec(
        String id,
        String name,
        BodyType type,
        double semiMajorAxis,  // AU
        long period,           // days
        double phaseOffset,    // radians
        double mass,           // arbitrary units
        double radius,         // arbitrary units
        String parentId        // null for heliocentric
    ) {}

    public static final List<BodySpec> BODIES = List.of(
        new BodySpec("mercury", "Mercury",     BodyType.ROCKY,     0.39,    88, 0.0,    0.06, 0.38, null),
        new BodySpec("venus",   "Venus",       BodyType.ROCKY,     0.72,   225, 1.2,    0.82, 0.95, null),
        new BodySpec("earth",   "Earth",       BodyType.ROCKY,     1.00,   365, 2.4,    1.00, 1.00, null),
        new BodySpec("mars",    "Mars",        BodyType.ROCKY,     1.52,   687, 0.6,    0.11, 0.53, null),
        new BodySpec("belt-a",  "Belt-A",      BodyType.ASTEROID,  2.40,  1390, 0.0,    0.001, 0.05, null),
        new BodySpec("belt-b",  "Belt-B",      BodyType.ASTEROID,  2.65,  1614, 1.7,    0.001, 0.04, null),
        new BodySpec("belt-c",  "Belt-C",      BodyType.ASTEROID,  2.90,  1856, 3.4,    0.002, 0.06, null),
        new BodySpec("jovian",  "Jovian",      BodyType.GAS_GIANT, 5.20,  4333, 4.1,  317.83,11.21, null),
        new BodySpec("io",      "Io",          BodyType.MOON,      0.003,    2, 0.0,    0.015, 0.286, "jovian"),
        new BodySpec("europa",  "Europa",      BodyType.ICE_BODY,  0.004,    4, 1.6,    0.008, 0.245, "jovian")
    );

    public static final String STARTING_SITE_BODY = "earth";
    public static final double STARTING_SITE_LAT = 0.5;   // ~28° N
    public static final double STARTING_SITE_LON = -1.5;  // arbitrary
}
