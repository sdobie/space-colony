package spacecolony.world;

import java.util.EnumMap;
import java.util.Map;
import spacecolony.sim.BodyType;
import spacecolony.sim.Resource;

/**
 * Per-body resource yield multiplier, sampled by (lat, lon). Backed by independent
 * SimplexNoise streams (one per resource), seeded from the body's surface seed mixed
 * with the resource ordinal. Body type sets a base bias and disables certain resources.
 *
 * Output is clamped to [0, 1] where 1 means rich and 0 means absent.
 */
public class ResourceYieldMap {
    private final BodyType type;
    private final Map<Resource, SimplexNoise> noises = new EnumMap<>(Resource.class);

    public ResourceYieldMap(long bodySeed, BodyType type) {
        this.type = type;
        for (Resource r : Resource.values()) {
            // Each resource gets its own noise stream.
            long seed = bodySeed * 31 + r.ordinal() * 1009L;
            noises.put(r, new SimplexNoise(seed));
        }
    }

    /** lat in radians [-pi/2, pi/2]; lon in radians [-pi, pi]. */
    public double sample(Resource r, double lat, double lon) {
        // Disabled resources (per body type)
        if (type == BodyType.GAS_GIANT) {
            switch (r) {
                case ORE, METAL, SILICATE, ICE, WATER, FOOD, BIOMASS, COMPONENTS -> { return 0.0; }
                default -> {}
            }
        }
        if (type == BodyType.ASTEROID) {
            switch (r) {
                case FUEL, FOOD, BIOMASS, ENERGY -> { return 0.0; }
                default -> {}
            }
        }
        // ENERGY is not stockpiled and isn't yielded by terrain.
        if (r == Resource.ENERGY) return 0.0;

        double base = baseBias(r);
        // Convert lat/lon to a 3D unit vector for seamless sampling.
        double cosLat = Math.cos(lat);
        double x = cosLat * Math.cos(lon);
        double y = cosLat * Math.sin(lon);
        double z = Math.sin(lat);

        SimplexNoise n = noises.get(r);
        // 4-octave fBm gives smooth blobs of high yield.
        double v = n.fractal(x * 2.0, y * 2.0, z * 2.0, 4, 0.5, 2.0);
        double normalized = (v + 1.0) * 0.5;
        return Math.max(0.0, Math.min(1.0, base * normalized));
    }

    private double baseBias(Resource r) {
        return switch (type) {
            case ROCKY -> switch (r) {
                case ORE, METAL, SILICATE -> 1.0;
                case FOOD, BIOMASS, WATER -> 0.4;
                case ICE -> 0.2;
                case FUEL -> 0.1;
                default -> 0.5;
            };
            case ASTEROID -> switch (r) {
                case ORE, METAL, SILICATE -> 1.2;
                case ICE, WATER -> 0.4;
                default -> 0.0;
            };
            case ICE_BODY -> switch (r) {
                case ICE, WATER -> 1.4;
                case ORE, METAL -> 0.3;
                default -> 0.0;
            };
            case GAS_GIANT -> switch (r) {
                case FUEL -> 1.5;
                default -> 0.0;
            };
            case MOON -> switch (r) {
                case ORE, METAL, SILICATE -> 0.8;
                case ICE, WATER -> 0.6;
                default -> 0.2;
            };
        };
    }
}
