package spacecolony.sim.phases;

import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
import spacecolony.sim.World;

public final class TransitPhase {
    // Tick rate for loading/unloading: each phase moves up to LOAD_RATE per resource per tick.
    private static final double LOAD_RATE = 25.0;
    private static final double FUEL_K = 0.5;

    private TransitPhase() {}

    public static void advanceTransits(World w) {
        for (Ship s : w.ships) {
            if (s.state == ShipState.IN_TRANSIT && w.tick >= s.transit.arrivalTick()) {
                s.state = ShipState.UNLOADING;
                s.currentSiteId = s.transit.destSiteId();
                // Keep s.transit for the destSiteId; we'll clear it when fully unloaded.
                w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.SHIP_ARRIVED,
                    "Ship " + s.name + " arrived at " + s.currentSiteId, null, s.currentSiteId, s.id));
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
                    double dist = distanceBetweenSitesAtTicks(w, s.transit.originSiteId(), s.transit.destSiteId(), depart, arrival);
                    double cost = FUEL_K * (s.shipClass.dryMass() + s.cargoMass()) * dist;
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
                                            depart, arrival, Transit.snapshot(s.cargo));
                    s.currentSiteId = null;
                    s.state = ShipState.IN_TRANSIT;
                    w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.SHIP_DEPARTED,
                        "Ship " + s.name + " departed for " + s.transit.destSiteId(),
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

    private static long computeArrivalTick(World w, Ship s, long depart) {
        double speed = s.shipClass.speed();
        Site origin = w.findSite(s.transit.originSiteId());
        Site dest = w.findSite(s.transit.destSiteId());
        if (origin == null || dest == null) return depart + 1;
        double[] op = OrbitalGeometry.bodyPosition(w, origin.bodyId, depart);
        long t = depart + 1;
        for (int iter = 0; iter < 6; iter++) {
            double[] dp = OrbitalGeometry.bodyPosition(w, dest.bodyId, t);
            double dx = dp[0] - op[0], dy = dp[1] - op[1];
            double dist = Math.sqrt(dx * dx + dy * dy);
            long newT = depart + (long) Math.ceil(dist / speed);
            if (newT == t) return t;
            t = newT;
        }
        return t;
    }

    private static double distanceBetweenSitesAtTicks(World w, String originId, String destId, long t0, long t1) {
        Site origin = w.findSite(originId);
        Site dest = w.findSite(destId);
        if (origin == null || dest == null) return 0.0;
        double[] op = OrbitalGeometry.bodyPosition(w, origin.bodyId, t0);
        double[] dp = OrbitalGeometry.bodyPosition(w, dest.bodyId, t1);
        double dx = dp[0] - op[0], dy = dp[1] - op[1];
        return Math.sqrt(dx * dx + dy * dy);
    }
}
