package spacecolony.sim;

import java.util.List;
import java.util.Map;

/**
 * The resources ships burn, best first, and what a unit of each is worth (Plan 10 §4). Trip
 * costs are in fuel value, so a trip on hydrogen burns the same units it always did and a trip
 * on synfuel burns twice as many.
 */
public final class Fuels {
    private Fuels() {}

    /** Draw and burn order: hydrogen first. */
    public static final List<Resource> ALL = List.of(Resource.HYDROGEN, Resource.SYNFUEL);
    public static final double SYNFUEL_VALUE = 0.5;
    private static final double EPS = 1e-9;

    public static double value(Resource r) {
        return switch (r) {
            case HYDROGEN -> 1.0;
            case SYNFUEL -> SYNFUEL_VALUE;
            default -> 0.0;
        };
    }

    public static boolean isFuel(Resource r) { return value(r) > 0; }

    /** Σ amount × value over the fuels in {@code amounts}. */
    public static double valueOf(Map<Resource, Double> amounts) {
        double v = 0.0;
        for (Resource r : ALL) v += Math.max(0.0, amounts.getOrDefault(r, 0.0)) * value(r);
        return v;
    }

    /**
     * Moves up to {@code value} worth of fuel from {@code from} into {@code to}, hydrogen first.
     * Returns the units moved of each fuel (index matches {@link #ALL}).
     */
    public static double[] transfer(Map<Resource, Double> from, Map<Resource, Double> to, double value) {
        double[] moved = new double[ALL.size()];
        double left = value;
        for (int i = 0; i < ALL.size() && left > EPS; i++) {
            Resource r = ALL.get(i);
            double have = Math.max(0.0, from.getOrDefault(r, 0.0));
            double units = Math.min(have, left / value(r));
            if (units <= 0) continue;
            from.put(r, have - units);
            to.merge(r, units, Double::sum);
            moved[i] = units;
            left -= units * value(r);
        }
        return moved;
    }

    /** Removes {@code value} worth of fuel from {@code tank}, hydrogen first, never below zero. */
    public static void burn(Map<Resource, Double> tank, double value) {
        double left = value;
        for (Resource r : ALL) {
            if (left <= EPS) break;
            double have = Math.max(0.0, tank.getOrDefault(r, 0.0));
            double units = Math.min(have, left / value(r));
            tank.put(r, have - units);
            left -= units * value(r);
        }
    }

    /** "60 hydrogen, 60 synfuel", whole units, empty fuels left out; "no fuel" when empty. */
    public static String describe(Map<Resource, Double> amounts) {
        StringBuilder sb = new StringBuilder();
        for (Resource r : ALL) {
            double v = amounts.getOrDefault(r, 0.0);
            if (v < 0.5) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(String.format("%.0f %s", v, r.name().toLowerCase()));
        }
        return sb.isEmpty() ? "no fuel" : sb.toString();
    }
}
