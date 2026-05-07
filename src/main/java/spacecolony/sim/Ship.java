package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

public class Ship {
    public final String id;
    public String name;
    public final ShipClass shipClass;
    public ShipState state;
    /** Site ID when state in {IDLE, LOADING, UNLOADING}; null when IN_TRANSIT. */
    public String currentSiteId;
    /** Non-null only when state == IN_TRANSIT. */
    public Transit transit;
    public final Map<Resource, Double> cargo = new EnumMap<>(Resource.class);
    public double fuel;

    public Ship(String id, String name, ShipClass shipClass, String homeSiteId) {
        this.id = id;
        this.name = name;
        this.shipClass = shipClass;
        this.state = ShipState.IDLE;
        this.currentSiteId = homeSiteId;
        this.transit = null;
        this.fuel = 0.0;
        for (Resource r : Resource.values()) cargo.put(r, 0.0);
    }

    public double cargoMass() {
        double m = 0.0;
        for (double v : cargo.values()) m += v;
        return m;
    }
}
