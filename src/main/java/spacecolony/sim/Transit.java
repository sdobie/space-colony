package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * A ship's trip. Site-bound trips set {@code destSiteId}; a colonizer's trip to a body with no
 * site sets {@code destBodyId} instead (exactly one of the two is non-null).
 */
public record Transit(
    String originSiteId,
    String destSiteId,
    String destBodyId,
    long departureTick,
    long arrivalTick,
    Map<Resource, Double> cargoSnapshot
) {
    /** Site-bound trip. */
    public Transit(String originSiteId, String destSiteId, long departureTick, long arrivalTick,
                   Map<Resource, Double> cargoSnapshot) {
        this(originSiteId, destSiteId, null, departureTick, arrivalTick, cargoSnapshot);
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
