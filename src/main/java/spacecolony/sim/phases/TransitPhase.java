package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.Transit;
import spacecolony.sim.World;

public final class TransitPhase {
    // Tick rate for loading/unloading: each phase moves up to LOAD_RATE per resource per tick.
    private static final double LOAD_RATE = 25.0;
    private static final double FUEL_K = 0.5;

    private TransitPhase() {}

    public static void advanceTransits(World w) {
        for (Ship s : w.ships) {
            if (s.state == ShipState.IN_TRANSIT && w.tick >= s.transit.arrivalTick()
                    && s.transit.destBodyId() != null) {
                // A colonizer's trip to a body: wait in orbit with the cargo aboard.
                s.state = ShipState.IDLE;
                s.orbitingBodyId = s.transit.destBodyId();
                s.transit = null;
                Body b = w.findBody(s.orbitingBodyId);
                String bodyName = b != null ? b.name : s.orbitingBodyId;
                w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.SHIP_ARRIVED,
                    "Colonizer " + s.name + " is orbiting " + bodyName, s.orbitingBodyId, null, s.id));
            } else if (s.state == ShipState.IN_TRANSIT && w.tick >= s.transit.arrivalTick()) {
                s.state = ShipState.UNLOADING;
                s.currentSiteId = s.transit.destSiteId();
                // Keep s.transit for the destSiteId; we'll clear it when fully unloaded.
                Site dest = w.findSite(s.currentSiteId);
                String destName = dest != null ? dest.name : s.currentSiteId;
                w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.SHIP_ARRIVED,
                    "Ship " + s.name + " arrived at " + destName, null, s.currentSiteId, s.id));
            }
        }
    }

    public static void loadingAndUnloading(World w) {
        for (Ship s : w.ships) {
            if (s.state == ShipState.LOADING) {
                Site origin = w.findSite(s.currentSiteId);
                if (origin == null) continue; // shouldn't happen
                // s.transit holds the manifest as cargoSnapshot
                boolean filled = true;
                for (var entry : s.transit.cargoSnapshot().entrySet()) {
                    Resource r = entry.getKey();
                    double target = entry.getValue();
                    double already = s.cargo.getOrDefault(r, 0.0);
                    if (already >= target) continue;
                    double need = target - already;
                    double avail = origin.stockpile.getOrDefault(r, 0.0);
                    double move = Math.min(LOAD_RATE, Math.min(need, avail));
                    if (move > 0) {
                        origin.stockpile.merge(r, -move, Double::sum);
                        s.cargo.merge(r, move, Double::sum);
                    }
                    if (s.cargo.getOrDefault(r, 0.0) + 1e-9 < target) filled = false;
                }
                if (filled) {
                    // Compute transit and depart.
                    long depart = w.tick;
                    long arrival = computeArrivalTick(w, s, depart);
                    double cost = fuelCostBetweenBodies(w, s.shipClass, s.cargoMass(),
                        origin.bodyId, s.transit.destBody(w), depart, arrival);
                    if (s.fuel < cost) {
                        // Draw this trip's shortfall from the origin's FUEL stock (after the
                        // manifest loaded, so shipped FUEL is taken first).
                        double have = origin.stockpile.getOrDefault(Resource.FUEL, 0.0);
                        double draw = Math.min(cost - s.fuel, have);
                        if (draw > 0) {
                            origin.stockpile.merge(Resource.FUEL, -draw, Double::sum);
                            s.fuel += draw;
                        }
                    }
                    if (s.fuel < cost) {
                        w.emit(new Event(w.tick, EventSeverity.WARNING, EventKind.SHIP_OUT_OF_FUEL,
                            "Ship " + s.name + " aborted: insufficient fuel", null, null, s.id));
                        // Return cargo to origin and reset state.
                        // Snapshot via EnumMap to preserve deterministic ordinal iteration order.
                        for (var entry : new java.util.EnumMap<>(s.cargo).entrySet()) {
                            if (entry.getValue() > 0) {
                                origin.stockpile.merge(entry.getKey(), entry.getValue(), Double::sum);
                                s.cargo.put(entry.getKey(), 0.0);
                            }
                        }
                        s.state = ShipState.IDLE;
                        s.transit = null;
                        continue;
                    }
                    s.fuel -= cost;
                    s.transit = new Transit(s.transit.originSiteId(), s.transit.destSiteId(),
                                            s.transit.destBodyId(), depart, arrival,
                                            Transit.snapshot(s.cargo));
                    s.currentSiteId = null;
                    s.state = ShipState.IN_TRANSIT;
                    w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.SHIP_DEPARTED,
                        "Ship " + s.name + " departed for "
                            + (s.transit.destSiteId() != null ? s.transit.destSiteId() : s.transit.destBodyId()),
                        null, null, s.id));
                }
            } else if (s.state == ShipState.UNLOADING) {
                Site dest = w.findSite(s.currentSiteId);
                if (dest == null) continue;
                boolean empty = true;
                for (Resource r : Resource.values()) {
                    double in = s.cargo.getOrDefault(r, 0.0);
                    if (in <= 1e-9) continue;
                    double move = Math.min(LOAD_RATE, in);
                    s.cargo.merge(r, -move, Double::sum);
                    dest.stockpile.merge(r, move, Double::sum);
                    if (s.cargo.getOrDefault(r, 0.0) > 1e-9) empty = false;
                }
                if (empty) {
                    s.state = ShipState.IDLE;
                    s.transit = null;
                }
            }
        }
    }

    /**
     * Fuel a ship of class {@code c} carrying {@code cargoMass} burns flying from the origin
     * site's body at {@code t0} to the destination site's body at {@code t1}, under the
     * world's current tech. Used at departure and (as an estimate) by the debug map overlay.
     */
    public static double fuelCost(World w, ShipClass c, double cargoMass,
                                  String originSiteId, String destSiteId, long t0, long t1) {
        return fuelCostBetweenBodies(w, c, cargoMass, bodyOfSite(w, originSiteId),
                                     bodyOfSite(w, destSiteId), t0, t1);
    }

    /** As {@link #fuelCost}, for a trip to a body that may have no site. */
    public static double fuelCostToBody(World w, ShipClass c, double cargoMass,
                                        String originSiteId, String destBodyId, long t0, long t1) {
        return fuelCostBetweenBodies(w, c, cargoMass, bodyOfSite(w, originSiteId), destBodyId, t0, t1);
    }

    private static double fuelCostBetweenBodies(World w, ShipClass c, double cargoMass,
                                                String originBodyId, String destBodyId, long t0, long t1) {
        double dist = distanceBetweenBodiesAtTicks(w, originBodyId, destBodyId, t0, t1);
        return FUEL_K * (c.dryMass() + cargoMass) * dist * TechEffects.fuelCostMultiplier(w.tech);
    }

    private static long computeArrivalTick(World w, Ship s, long depart) {
        double speed = s.shipClass.speed();
        String originBody = bodyOfSite(w, s.transit.originSiteId());
        String destBody = s.transit.destBody(w);
        // A hop between sites on one body takes a day and ignores the body's own orbital motion.
        if (originBody == null || destBody == null || originBody.equals(destBody)) return depart + 1;
        double[] op = OrbitalGeometry.bodyPosition(w, originBody, depart);
        long t = depart + 1;
        for (int iter = 0; iter < 6; iter++) {
            double[] dp = OrbitalGeometry.bodyPosition(w, destBody, t);
            double dx = dp[0] - op[0], dy = dp[1] - op[1];
            double dist = Math.sqrt(dx * dx + dy * dy);
            long newT = depart + (long) Math.ceil(dist / speed);
            if (newT == t) return t;
            t = newT;
        }
        return t;
    }

    private static String bodyOfSite(World w, String siteId) {
        Site s = siteId == null ? null : w.findSite(siteId);
        return s == null ? null : s.bodyId;
    }

    private static double distanceBetweenBodiesAtTicks(World w, String originBodyId, String destBodyId,
                                                       long t0, long t1) {
        // Same body: the sites travel together, so the trip is free (not the planet's day of orbit).
        if (originBodyId == null || destBodyId == null || originBodyId.equals(destBodyId)) return 0.0;
        double[] op = OrbitalGeometry.bodyPosition(w, originBodyId, t0);
        double[] dp = OrbitalGeometry.bodyPosition(w, destBodyId, t1);
        double dx = dp[0] - op[0], dy = dp[1] - op[1];
        return Math.sqrt(dx * dx + dy * dy);
    }
}
