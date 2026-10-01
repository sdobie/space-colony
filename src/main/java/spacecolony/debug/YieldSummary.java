package spacecolony.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;

/**
 * Per-body mean resource yields for the debug map labels. Samples {@link ResourceSurvey}'s 8×16 lat/lon grid
 * and caches by body id; clear on WorldReplaced, because yields depend on the seed.
 */
public final class YieldSummary {
    public record Entry(Resource resource, double mean) {}

    private final Map<String, List<Entry>> cache = new HashMap<>();

    public List<Entry> top(Body b, int k) {
        String key = b.id + "#" + k;
        List<Entry> hit = cache.get(key);
        if (hit != null) return hit;
        List<Entry> result = compute(b, k);
        cache.put(key, result);
        return result;
    }

    public void clear() { cache.clear(); }

    private static List<Entry> compute(Body b, int k) {
        if (b.resourceYields == null) return List.of();
        List<Entry> all = new ArrayList<>();
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            double sum = 0;
            int n = 0;
            for (int i = 0; i < 8; i++) {
                double lat = ResourceSurvey.gridLat(i);
                for (int j = 0; j < 16; j++) {
                    double lon = ResourceSurvey.gridLon(j);
                    sum += b.resourceYields.sample(r, lat, lon);
                    n++;
                }
            }
            all.add(new Entry(r, sum / n));
        }
        all.sort(Comparator.comparingDouble(Entry::mean).reversed());
        return List.copyOf(all.subList(0, Math.min(k, all.size())));
    }

    /** {@code ORE .62 · ICE .41 · SIL .30}. */
    public static String format(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(" · ");
            String mean = String.format("%.2f", e.mean());
            if (mean.startsWith("0")) mean = mean.substring(1);
            sb.append(e.resource().name(), 0, 3).append(' ').append(mean);
        }
        return sb.toString();
    }
}
