package spacecolony.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What one construction step takes: resources paid up front, then days until it's done. */
public record BuildCost(Map<Resource, Double> resources, int days) {
    public BuildCost {
        resources = Collections.unmodifiableMap(new EnumMap<>(resources));
    }

    public static BuildCost of(double metal, double components, int days) {
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        m.put(Resource.METAL, metal);
        m.put(Resource.COMPONENTS, components);
        return new BuildCost(m, days);
    }

    /** True when {@code stock} holds every resource this costs. */
    public boolean affordable(Map<Resource, Double> stock) {
        return shortfall(stock).isEmpty();
    }

    /** The missing amount of each resource {@code stock} doesn't cover; empty when affordable. */
    public Map<Resource, Double> shortfall(Map<Resource, Double> stock) {
        Map<Resource, Double> out = new EnumMap<>(Resource.class);
        for (var e : resources.entrySet()) {
            double have = stock.getOrDefault(e.getKey(), 0.0);
            if (have + 1e-9 < e.getValue()) out.put(e.getKey(), e.getValue() - have);
        }
        return out;
    }

    /** Each amount × {@code factor}, rounded up to a whole unit; days unchanged. */
    public BuildCost scaled(double factor) {
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        for (var e : resources.entrySet()) m.put(e.getKey(), Math.ceil(e.getValue() * factor - 1e-9));
        return new BuildCost(m, days);
    }

    /** "15 METAL, 5 COMPONENTS · 3 days" (days left off when 0). */
    public String describe() {
        String res = describe(resources);
        return days > 0 ? res + " · " + days + (days == 1 ? " day" : " days") : res;
    }

    /** "15 METAL, 5 COMPONENTS", whole units, zero entries skipped; "nothing" when empty. */
    public static String describe(Map<Resource, Double> amounts) {
        List<String> parts = new ArrayList<>();
        for (var e : amounts.entrySet())
            if (e.getValue() > 1e-9) parts.add(String.format("%.0f %s", e.getValue(), e.getKey()));
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }
}
