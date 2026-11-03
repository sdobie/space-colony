package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;

public class Ship {
    public final String id;
    public String name;
    public final ShipClass shipClass;
    public ShipState state;
    /**
     * Site ID when state in {IDLE, LOADING, UNLOADING}; null when IN_TRANSIT, or when IDLE in
     * orbit of {@link #orbitingBodyId}.
     */
    public String currentSiteId;
    /** Body a colonizer is waiting at after a trip to a body with no site; null otherwise. */
    public String orbitingBodyId;
    /**
     * Non-null when state in {LOADING, IN_TRANSIT, UNLOADING}.
     * - In LOADING: holds destSiteId and the cargo manifest target; arrivalTick is
     *   {@link Transit#PENDING_ARRIVAL_TICK} until the manifest is filled and the
     *   ship physically departs.
     * - In IN_TRANSIT and UNLOADING: arrivalTick is the real computed arrival tick.
     */
    public Transit transit;
    /** Work-days left while {@link ShipState#BUILDING}; 0 once built. */
    public int buildDaysLeft;
    /** The shipyard building (by its per-site id) building this ship; 0 once built. */
    public int yardBuildingId;
    public final Map<Resource, Double> cargo = new EnumMap<>(Resource.class);
    /** Fuel aboard, per fuel (Plan 10): HYDROGEN and SYNFUEL. */
    public final Map<Resource, Double> tank = new EnumMap<>(Resource.class);

    public Ship(String id, String name, ShipClass shipClass, String homeSiteId) {
        this.id = id;
        this.name = name;
        this.shipClass = shipClass;
        this.state = ShipState.IDLE;
        this.currentSiteId = homeSiteId;
        this.transit = null;
        for (Resource r : Fuels.ALL) tank.put(r, 0.0);
        for (Resource r : Resource.values()) cargo.put(r, 0.0);
    }

    /** What the tank is worth in fuel value (hydrogen 1, synfuel 0.5 per unit). */
    public double fuelValue() { return Fuels.valueOf(tank); }

    public void setTank(Resource fuel, double amount) { tank.put(fuel, amount); }

    public double cargoMass() {
        double m = 0.0;
        for (double v : cargo.values()) m += v;
        return m;
    }
}
