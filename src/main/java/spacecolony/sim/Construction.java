package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * The rules for building, upgrading, repairing, cancelling and demolishing (Plan 8 §3).
 * {@code CommandPhase} enforces them; the UI asks the {@code whyNot…} methods for its
 * explanations, so a rejected command reads the same as the disabled button that would
 * have prevented it.
 */
public final class Construction {
    private Construction() {}

    /** Building slots per colony; buildings under construction use one too. */
    public static final int SLOTS = 10;
    public static final int MAX_LEVEL = 5;
    /** An upgrade to level L costs the base cost × L × this. */
    static final double UPGRADE_STEP = 0.5;
    static final double REPAIR = 0.25;
    static final double DEMOLISH_REFUND = 0.5;

    public static int slotsUsed(Site s) { return s.buildings.size(); }

    public static BuildCost buildCost(BuildingType t) { return BuildingCatalog.get(t).cost(); }

    public static BuildCost upgradeCost(BuildingType t, int toLevel) {
        return buildCost(t).scaled(toLevel * UPGRADE_STEP);
    }

    /** Repairs are instant, so the cost has no days. */
    public static BuildCost repairCost(BuildingType t) {
        BuildCost c = buildCost(t).scaled(REPAIR);
        return new BuildCost(c.resources(), 0);
    }

    /** Everything put into {@code b} for its current level: the base cost plus each upgrade step. */
    public static Map<Resource, Double> invested(Building b) {
        Map<Resource, Double> out = new EnumMap<>(Resource.class);
        if (b.level >= 1) add(out, buildCost(b.type).resources(), 1.0);
        for (int l = 2; l <= b.level; l++) add(out, upgradeCost(b.type, l).resources(), 1.0);
        return out;
    }

    /** Half of what was invested, rounded down per resource. */
    public static Map<Resource, Double> demolishRefund(Building b) {
        Map<Resource, Double> out = new EnumMap<>(Resource.class);
        for (var e : invested(b).entrySet()) {
            double v = Math.floor(e.getValue() * DEMOLISH_REFUND + 1e-9);
            if (v > 0) out.put(e.getKey(), v);
        }
        return out;
    }

    /** The step under way, in full: the base cost for a new building, else the upgrade's. */
    public static Map<Resource, Double> cancelRefund(Building b) {
        if (!b.isUnderConstruction()) return new EnumMap<>(Resource.class);
        BuildCost step = b.level == 0 ? buildCost(b.type) : upgradeCost(b.type, b.level + 1);
        return new EnumMap<>(step.resources());
    }

    /** Why {@code s} can't start a new {@code t} now, or null. */
    public static String whyNotBuild(Site s, BuildingType t) {
        if (slotsUsed(s) >= SLOTS) return "No free building slot (" + slotsUsed(s) + " of " + SLOTS + ")";
        return shortfallText(buildCost(t), s.stockpile);
    }

    public static String whyNotUpgrade(Site s, Building b) {
        if (b.level == 0 || b.isUnderConstruction()) return "Under construction";
        if (b.level >= MAX_LEVEL) return "Already level " + MAX_LEVEL;
        if (!b.enabled) return "Damaged: repair it first";
        return shortfallText(upgradeCost(b.type, b.level + 1), s.stockpile);
    }

    public static String whyNotRepair(Site s, Building b) {
        if (b.type == BuildingType.POWER_PLANT) return "Power plants come back on their own the next day";
        if (b.enabled) return "Not damaged";
        return shortfallText(repairCost(b.type), s.stockpile);
    }

    public static String whyNotCancel(Building b) {
        return b.isUnderConstruction() ? null : "Nothing under construction";
    }

    public static String whyNotDemolish(Building b) {
        return b.isUnderConstruction() ? "Under construction: cancel it instead" : null;
    }

    /**
     * "Need 12 more METAL (have 8 of 20)", naming the first missing resource and adding
     * "and 3 more COMPONENTS" for each other one; null when {@code stock} covers the cost.
     */
    static String shortfallText(BuildCost cost, Map<Resource, Double> stock) {
        Map<Resource, Double> missing = cost.shortfall(stock);
        if (missing.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (var e : missing.entrySet()) {
            Resource r = e.getKey();
            if (sb.isEmpty()) {
                sb.append(String.format("Need %s more %s (have %s of %s)", whole(e.getValue()), r,
                    have(stock.getOrDefault(r, 0.0)), whole(cost.resources().get(r))));
            } else {
                sb.append(String.format(" and %s more %s", whole(e.getValue()), r));
            }
        }
        return sb.toString();
    }

    /** Whole units, rounding a shortfall up so "need 0" never appears. */
    private static String whole(double v) { return String.format("%.0f", Math.ceil(v - 1e-9)); }

    /** Whole units held, rounded down, never "-0". */
    private static String have(double v) { return String.format("%.0f", Math.max(0.0, Math.floor(v + 1e-9))); }

    private static void add(Map<Resource, Double> into, Map<Resource, Double> from, double factor) {
        for (var e : from.entrySet()) into.merge(e.getKey(), e.getValue() * factor, Double::sum);
    }
}
