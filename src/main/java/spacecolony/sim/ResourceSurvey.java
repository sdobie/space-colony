package spacecolony.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a survey of one body shows: for each resource the ground feeds, the average yield and
 * the best spot on a fixed 8 × 16 latitude/longitude grid. Yield maps are fixed at world-gen,
 * so results are cached by body id and surface seed.
 */
public final class ResourceSurvey {
    /** Resources the economy reads from the ground (mines and farms). */
    public static final List<Resource> SURVEYED = List.of(
        Resource.ORE, Resource.SILICATE, Resource.ICE, Resource.BIOMASS, Resource.FUEL);
    /** A resource whose best spot is below this is "none" and left out. */
    public static final double NONE_BELOW = 0.02;
    public static final int GRID_LAT = 8;
    public static final int GRID_LON = 16;

    public enum Rating {
        RICH("Rich"), GOOD("Good"), FAIR("Fair"), POOR("Poor");
        private final String label;
        Rating(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Entry(Resource resource, double average, double best, double bestLat, double bestLon) {
        public Rating rating() { return ResourceSurvey.rating(best); }
    }

    private static final Map<String, List<Entry>> CACHE = new ConcurrentHashMap<>();

    private ResourceSurvey() {}

    /** Grid latitude for row {@code i} (radians). */
    public static double gridLat(int i) { return -Math.PI / 2 + Math.PI / 16 + i * Math.PI / 8; }
    /** Grid longitude for column {@code j} (radians). */
    public static double gridLon(int j) { return -Math.PI + Math.PI / 16 + j * Math.PI / 8; }

    /** The survey of {@code b}, best spot first; empty when the body has no yield map. */
    public static List<Entry> of(Body b) {
        if (b == null || b.resourceYields == null) return List.of();
        return CACHE.computeIfAbsent(b.id + "#" + b.surfaceSeed, k -> compute(b));
    }

    public static Rating rating(double y) {
        if (y >= 0.6) return Rating.RICH;
        if (y >= 0.4) return Rating.GOOD;
        if (y >= 0.2) return Rating.FAIR;
        return Rating.POOR;
    }

    /** Resources worth reporting at one spot: the surveyed ones the body offers, with their yield there. */
    public static List<Entry> at(Body b, double lat, double lon) {
        List<Entry> out = new ArrayList<>();
        if (b == null || b.resourceYields == null) return out;
        for (Resource r : SURVEYED) {
            double y = b.resourceYields.sample(r, lat, lon);
            if (y >= NONE_BELOW) out.add(new Entry(r, y, y, lat, lon));
        }
        return out;
    }

    /** {@code .83}: two decimals, leading zero dropped. */
    public static String fmt(double y) {
        String s = String.format("%.2f", y);
        return s.startsWith("0") ? s.substring(1) : s;
    }

    /** Three-letter name, e.g. SIL for SILICATE. */
    public static String abbrev(Resource r) { return r.name().substring(0, 3); }

    public static void clearCache() { CACHE.clear(); }

    private static List<Entry> compute(Body b) {
        List<Entry> all = new ArrayList<>();
        for (Resource r : SURVEYED) {
            double sum = 0, best = -1, bestLat = 0, bestLon = 0;
            int n = 0;
            for (int i = 0; i < GRID_LAT; i++) {
                double lat = gridLat(i);
                for (int j = 0; j < GRID_LON; j++) {
                    double lon = gridLon(j);
                    double y = b.resourceYields.sample(r, lat, lon);
                    sum += y;
                    n++;
                    if (y > best) { best = y; bestLat = lat; bestLon = lon; }
                }
            }
            if (best >= NONE_BELOW) all.add(new Entry(r, sum / n, best, bestLat, bestLon));
        }
        all.sort(Comparator.comparingDouble(Entry::best).reversed());
        return List.copyOf(all);
    }
}
