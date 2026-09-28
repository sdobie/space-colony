package spacecolony.sim.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import spacecolony.sim.Resource;

/**
 * One colony's day, itemised. Built by {@link SiteEconomy} and extended by the production
 * phase (storage clipping) and the transit phase (shipping); the UI only reads it.
 */
public final class DayReport {
    public double powerMade;
    public double powerUsed;
    public double powerFactor = 1.0;
    /** True for a dry run (shown after a load, before the first tick). */
    public boolean estimate;

    private final List<FlowLine> lines = new ArrayList<>();
    private final List<BuildingOutcome> buildings = new ArrayList<>();

    public void add(FlowLine l) { lines.add(l); }

    void addOutcome(BuildingOutcome o) { buildings.add(o); }

    /** Every line in the order it happened. */
    public List<FlowLine> lines() { return Collections.unmodifiableList(lines); }

    public List<BuildingOutcome> buildings() { return Collections.unmodifiableList(buildings); }

    /** Outcome of the building at {@code index} in the site's list, or null. */
    public BuildingOutcome outcome(int index) {
        for (BuildingOutcome o : buildings) if (o.index() == index) return o;
        return null;
    }

    /** All movement of {@code r}, summed in the order it happened. */
    public double net(Resource r) {
        double sum = 0.0;
        for (FlowLine l : lines) if (l.resource() == r) sum += l.amount();
        return sum;
    }

    /**
     * Building and population movement only, summed in the order it happened: what
     * {@code Site.productionRateCache} has always held (gross of clipping and shipping).
     */
    public double productionNet(Resource r) {
        double sum = 0.0;
        for (FlowLine l : lines) {
            if (l.resource() != r) continue;
            if (l.source() instanceof FlowSource.Building || l.source() instanceof FlowSource.Population)
                sum += l.amount();
        }
        return sum;
    }

    /** Lines that moved or wanted to move {@code r}: producers largest first, then consumers largest first. */
    public List<FlowLine> linesFor(Resource r) {
        List<FlowLine> out = new ArrayList<>();
        for (FlowLine l : lines) {
            if (l.resource() != r) continue;
            if (Math.abs(l.amount()) <= 1e-9 && Math.abs(l.wanted()) <= 1e-9) continue;
            out.add(l);
        }
        out.sort(Comparator.comparingInt((FlowLine l) -> sign(l) > 0 ? 0 : 1)
            .thenComparing(l -> -Math.abs(l.amount())));
        return out;
    }

    /** Lines for one building, in order. */
    public List<FlowLine> linesOf(int buildingIndex) {
        List<FlowLine> out = new ArrayList<>();
        for (FlowLine l : lines)
            if (l.source() instanceof FlowSource.Building b && b.index() == buildingIndex) out.add(l);
        return out;
    }

    /** True when any line touches {@code r}. */
    public boolean touches(Resource r) {
        for (FlowLine l : lines)
            if (l.resource() == r && (Math.abs(l.amount()) > 1e-9 || Math.abs(l.wanted()) > 1e-9)) return true;
        return false;
    }

    private static double sign(FlowLine l) {
        double v = Math.abs(l.amount()) > 1e-9 ? l.amount() : l.wanted();
        return Math.signum(v);
    }
}
