package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * A ship's trip. Site-bound trips set {@code destSiteId}; a trip to a body with no site
 * (colonizers and explorers) sets {@code destBodyId} instead (exactly one of the two is
 * non-null). A trip that starts at a site sets {@code originSiteId}; an explorer leaving orbit
 * sets {@code originBodyId} instead.
 */
public record Transit(
    String originSiteId,
    String destSiteId,
    String destBodyId,
    long departureTick,
    long arrivalTick,
    Map<Resource, Double> cargoSnapshot,
    String originBodyId
) {
    /** A trip that starts at a site. */
    public Transit(String originSiteId, String destSiteId, String destBodyId, long departureTick,
                   long arrivalTick, Map<Resource, Double> cargoSnapshot) {
        this(originSiteId, destSiteId, destBodyId, departureTick, arrivalTick, cargoSnapshot, null);
    }

    /** Site-bound trip. */
    public Transit(String originSiteId, String destSiteId, long departureTick, long arrivalTick,
                   Map<Resource, Double> cargoSnapshot) {
        this(originSiteId, destSiteId, null, departureTick, arrivalTick, cargoSnapshot);
    }

    /** The body this trip starts from: the origin site's body, or {@link #originBodyId}. */
    public String originBody(World w) {
        if (originBodyId != null) return originBodyId;
        Site s = originSiteId == null ? null : w.findSite(originSiteId);
        return s == null ? null : s.bodyId;
    }

    /** The body this trip ends at: the destination site's body, or {@link #destBodyId}. */
    public String destBody(World w) {
        if (destBodyId != null) return destBodyId;
        Site s = w.findSite(destSiteId);
        return s == null ? null : s.bodyId;
    }

    /**
     * Sentinel arrival tick used while a ship is in LOADING state. The loading/unloading
     * phase replaces the Transit at departure with a real arrivalTick computed from
     * orbital positions.
     */
    public static final long PENDING_ARRIVAL_TICK = -1L;

    public static Map<Resource, Double> snapshot(Map<Resource, Double> source) {
        Map<Resource, Double> out = new EnumMap<>(Resource.class);
        out.putAll(source);
        return out;
    }
}
