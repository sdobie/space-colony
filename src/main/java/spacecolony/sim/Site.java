package spacecolony.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One colony at a fixed (lat, lon) on a body. Aggregate stats only — no per-building grid.
 */
public class Site {
    public final String id;
    public String name;
    public final String bodyId;
    public final double lat;
    public final double lon;
    /** Baseline population cap before HABITAT boost. 200 for Earth Hub, 100 for colonizer-planted. */
    public final int siteBase;
    public int population;
    public int populationCap;
    public double morale; // 0.0 .. 1.0
    public final Map<Resource, Double> stockpile = new EnumMap<>(Resource.class);
    public final Map<Resource, Double> stockpileCap = new EnumMap<>(Resource.class);
    /** Last computed net production rate per day; refreshed each tick. */
    public final Map<Resource, Double> productionRateCache = new EnumMap<>(Resource.class);
    public final List<Building> buildings = new ArrayList<>();

    public Site(String id, String name, String bodyId, double lat, double lon, int siteBase) {
        this.id = id;
        this.name = name;
        this.bodyId = bodyId;
        this.lat = lat;
        this.lon = lon;
        this.siteBase = siteBase;
        this.populationCap = siteBase;   // initial cap before any HABITAT boost; recomputed each tick
        this.morale = 1.0;
        for (Resource r : Resource.values()) {
            stockpile.put(r, 0.0);
            stockpileCap.put(r, 1000.0);
            productionRateCache.put(r, 0.0);
        }
    }
}
