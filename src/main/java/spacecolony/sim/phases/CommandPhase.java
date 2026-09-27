package spacecolony.sim.phases;

import java.util.Deque;
import java.util.List;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Tech;
import spacecolony.sim.TechAvailability;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.TechEffects;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.Command;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.sim.commands.RetireShipCommand;

public final class CommandPhase {
    private CommandPhase() {}

    private static final double FUEL_K = 0.5;

    public static void drain(World w, Deque<Command> queue) {
        while (!queue.isEmpty()) {
            Command c = queue.removeFirst();
            try {
                apply(w, c);
            } catch (CommandRejectedException ex) {
                w.emit(new Event(w.tick, EventSeverity.WARNING, EventKind.COMMAND_REJECTED,
                    ex.getMessage(), null, null, null));
            }
        }
    }

    /**
     * Switch over the sealed Command hierarchy gives compile-time exhaustiveness:
     * adding a new permitee causes a compile error here until handled.
     */
    private static void apply(World w, Command c) {
        switch (c) {
            case BuildBuildingCommand bb -> applyBuildBuilding(w, bb);
            case BuildShipCommand bs     -> applyBuildShip(w, bs);
            case RetireShipCommand rs    -> applyRetireShip(w, rs);
            case QueueResearchCommand qr -> applyQueueResearch(w, qr);
            case BuildSiteCommand bsc    -> applyBuildSite(w, bsc);
            case DispatchShipCommand ds  -> applyDispatchShip(w, ds);
        }
    }

    private static void applyBuildBuilding(World w, BuildBuildingCommand bb) {
        Site s = w.findSite(bb.siteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bb.siteId());
        s.buildings.add(new Building(bb.type(), 1));
    }

    private static void applyBuildShip(World w, BuildShipCommand bs) {
        Site s = w.findSite(bs.shipyardSiteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bs.shipyardSiteId());
        boolean hasYard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.enabled);
        if (!hasYard) throw new CommandRejectedException("Site has no shipyard: " + bs.shipyardSiteId());
        if (w.findShip(bs.shipId()) != null) throw new CommandRejectedException("Ship id taken: " + bs.shipId());
        w.ships.add(new Ship(bs.shipId(), bs.name(), bs.shipClass(), bs.shipyardSiteId()));
    }

    private static void applyRetireShip(World w, RetireShipCommand rs) {
        Ship s = w.findShip(rs.shipId());
        if (s == null) throw new CommandRejectedException("No such ship: " + rs.shipId());
        if (s.state == ShipState.IN_TRANSIT) throw new CommandRejectedException("Cannot retire ship in transit");
        // A LOADING or UNLOADING ship is docked with cargo aboard; hand it to the site it's
        // docked at rather than scrapping it with the hull.
        Site dock = w.findSite(s.currentSiteId);
        if (dock != null) {
            for (var entry : s.cargo.entrySet()) {
                if (entry.getValue() > 0) dock.stockpile.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        w.ships.remove(s);
    }

    private static void applyQueueResearch(World w, QueueResearchCommand qr) {
        Tech t = TechCatalog.get(qr.techId());
        if (t == null) throw new CommandRejectedException("Unknown tech: " + qr.techId());
        if (w.tech.researched.contains(qr.techId())) throw new CommandRejectedException("Already researched: " + qr.techId());
        List<String> missing = TechAvailability.missingPrereqs(w.tech, t);
        if (!missing.isEmpty()) {
            List<String> names = missing.stream().map(id -> TechCatalog.get(id).name()).toList();
            throw new CommandRejectedException("Missing prerequisites for " + t.name() + ": " + String.join(", ", names));
        }
        // Preserve any accumulated points from goal rewards or a previously queued tech
        // (a player switching research mid-stream gets to carry their progress forward).
        w.tech.activeId = qr.techId();
    }

    private static void applyBuildSite(World w, BuildSiteCommand bsc) {
        // Validate everything before mutating, so a half-built site never lingers on a body.
        Body body = w.findBody(bsc.bodyId());
        if (body == null) throw new CommandRejectedException("No such body: " + bsc.bodyId());
        Ship colonizer = w.findShip(bsc.colonizerShipId());
        if (colonizer == null || colonizer.shipClass != ShipClass.COLONIZER)
            throw new CommandRejectedException("Need a COLONIZER ship at the body");
        if (!body.id.equals(currentBodyOf(w, colonizer)))
            throw new CommandRejectedException("Colonizer not at target body");
        if (w.findSite(bsc.siteId()) != null)
            throw new CommandRejectedException("Site id taken: " + bsc.siteId());
        Site s = new Site(bsc.siteId(), bsc.name(), bsc.bodyId(), bsc.lat(), bsc.lon(), 100);
        s.buildings.add(new Building(BuildingType.HABITAT, 1));
        body.sites.add(s);
        w.ships.remove(colonizer); // colonizer is consumed
    }

    private static void applyDispatchShip(World w, DispatchShipCommand ds) {
        Ship s = w.findShip(ds.shipId());
        if (s == null) throw new CommandRejectedException("No such ship: " + ds.shipId());
        if (s.state != ShipState.IDLE) throw new CommandRejectedException("Ship not idle: " + ds.shipId());
        Site originSite = w.findSite(s.currentSiteId);
        Site destSite = w.findSite(ds.destSiteId());
        if (destSite == null) throw new CommandRejectedException("No such dest: " + ds.destSiteId());
        // Spec §3.7: estimate fuel at command time using current positions and reject if
        // the ship clearly can't afford the manifest. The departure-time check still runs
        // later, but this saves the player N ticks of LOADING for a doomed dispatch.
        if (originSite != null) {
            double[] op = OrbitalGeometry.bodyPosition(w, originSite.bodyId, w.tick);
            double[] dp = OrbitalGeometry.bodyPosition(w, destSite.bodyId, w.tick);
            double dx = dp[0] - op[0], dy = dp[1] - op[1];
            double dist = Math.sqrt(dx * dx + dy * dy);
            double manifestMass = 0.0;
            for (Double v : ds.manifest().values()) if (v != null) manifestMass += v;
            double estCost = FUEL_K * (s.shipClass.dryMass() + manifestMass) * dist
                           * TechEffects.fuelCostMultiplier(w.tech);
            if (s.fuel < estCost)
                throw new CommandRejectedException("Insufficient fuel for dispatch: " + ds.shipId());
        }
        // Move into LOADING; transit math runs in loadingAndUnloading() when manifest is filled.
        // Stash dest + manifest on Transit with PENDING_ARRIVAL_TICK; loadingAndUnloading()
        // recomputes the real arrival tick at departure.
        s.state = ShipState.LOADING;
        s.transit = new Transit(s.currentSiteId, ds.destSiteId(), w.tick,
                                Transit.PENDING_ARRIVAL_TICK, Transit.snapshot(ds.manifest()));
    }

    private static String currentBodyOf(World w, Ship s) {
        if (s.currentSiteId == null) return null;
        Site site = w.findSite(s.currentSiteId);
        return site == null ? null : site.bodyId;
    }

    static class CommandRejectedException extends RuntimeException {
        CommandRejectedException(String m) { super(m); }
    }
}
