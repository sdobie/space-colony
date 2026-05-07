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
    public static Map<Resource, Double> snapshot(Map<Resource, Double> source) {
        Map<Resource, Double> out = new EnumMap<>(Resource.class);
        out.putAll(source);
        return out;
    }
}
