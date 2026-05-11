package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

public record Transit(
    String originSiteId,
    String destSiteId,
    long departureTick,
    long arrivalTick,
    Map<Resource, Double> cargoSnapshot
) {
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
