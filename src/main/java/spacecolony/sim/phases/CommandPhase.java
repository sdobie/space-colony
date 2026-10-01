package spacecolony.sim.phases;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildCost;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Construction;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
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
import spacecolony.sim.commands.CancelConstructionCommand;
import spacecolony.sim.commands.Command;
import spacecolony.sim.commands.DemolishBuildingCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.sim.commands.RepairBuildingCommand;
import spacecolony.sim.commands.RetireShipCommand;
import spacecolony.sim.commands.SetRandomEventsCommand;
import spacecolony.sim.commands.UpgradeBuildingCommand;

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
            case SetRandomEventsCommand re -> w.randomEventsEnabled = re.enabled();
            case UpgradeBuildingCommand ub -> applyUpgrade(w, ub);
            case RepairBuildingCommand rb -> applyRepair(w, rb);
            case CancelConstructionCommand cc -> applyCancel(w, cc);
            case DemolishBuildingCommand db -> applyDemolish(w, db);
        }
    }

    private static void applyBuildBuilding(World w, BuildBuildingCommand bb) {
        Site s = w.findSite(bb.siteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bb.siteId());
        String why = Construction.whyNotBuild(s, bb.type());
        if (why != null) throw new CommandRejectedException(why);
        BuildCost cost = Construction.buildCost(bb.type());
        pay(s, cost);
        Building b = new Building(bb.type(), 0);
        b.daysLeft = cost.days();
        s.addBuilding(b);
    }

    private static void applyUpgrade(World w, UpgradeBuildingCommand c) {
        Site s = site(w, c.siteId());
        Building b = building(s, c.buildingId());
        String why = Construction.whyNotUpgrade(s, b);
        if (why != null) throw new CommandRejectedException(why);
        BuildCost cost = Construction.upgradeCost(b.type, b.level + 1);
        pay(s, cost);
        b.daysLeft = cost.days();
    }

    private static void applyRepair(World w, RepairBuildingCommand c) {
        Site s = site(w, c.siteId());
        Building b = building(s, c.buildingId());
        String why = Construction.whyNotRepair(s, b);
        if (why != null) throw new CommandRejectedException(why);
        pay(s, Construction.repairCost(b.type));
        b.enabled = true;
    }

    private static void applyCancel(World w, CancelConstructionCommand c) {
        Site s = site(w, c.siteId());
        Building b = building(s, c.buildingId());
        String why = Construction.whyNotCancel(b);
        if (why != null) throw new CommandRejectedException(why);
        refund(s, Construction.cancelRefund(b));
        if (b.level == 0) s.buildings.remove(b);
        else b.daysLeft = 0;
    }

    private static void applyDemolish(World w, DemolishBuildingCommand c) {
        Site s = site(w, c.siteId());
        Building b = building(s, c.buildingId());
        String why = Construction.whyNotDemolish(b);
        if (why != null) throw new CommandRejectedException(why);
        refund(s, Construction.demolishRefund(b));
        s.buildings.remove(b);
    }

    private static Site site(World w, String siteId) {
        Site s = w.findSite(siteId);
        if (s == null) throw new CommandRejectedException("No such site: " + siteId);
        return s;
    }

    private static Building building(Site s, int id) {
        Building b = s.findBuilding(id);
        if (b == null) throw new CommandRejectedException("No such building at " + s.name + ": " + id);
        return b;
    }

    private static void pay(Site s, BuildCost cost) {
        for (var e : cost.resources().entrySet())
            s.stockpile.put(e.getKey(), Math.max(0.0, s.stockpile.getOrDefault(e.getKey(), 0.0) - e.getValue()));
    }

    /** Adds each amount up to the stockpile's free room; the rest is lost (Plan 8 §3.6). */
    private static void refund(Site s, Map<Resource, Double> amounts) {
        for (var e : amounts.entrySet()) {
            Resource r = e.getKey();
            double have = s.stockpile.getOrDefault(r, 0.0);
            double room = Math.max(0.0, s.stockpileCap.getOrDefault(r, 1000.0) - have);
            s.stockpile.put(r, have + Math.min(e.getValue(), room));
        }
    }

    private static void applyBuildShip(World w, BuildShipCommand bs) {
        Site s = w.findSite(bs.shipyardSiteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bs.shipyardSiteId());
        boolean hasYard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.isOperational());
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
            // Its tank drains back into the dock's FUEL too.
            if (s.fuel > 1e-9) dock.stockpile.merge(Resource.FUEL, s.fuel, Double::sum);
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
        s.addBuilding(new Building(BuildingType.HABITAT, 1));
        body.sites.add(s);
        w.survey(body.id, bsc.name());
        // The colonizer's cargo becomes the new colony's starting stock.
        for (var e : colonizer.cargo.entrySet()) {
            if (e.getValue() > 1e-9) s.stockpile.merge(e.getKey(), e.getValue(), Double::sum);
        }
        // So does whatever fuel is left in its tank.
        if (colonizer.fuel > 1e-9) s.stockpile.merge(Resource.FUEL, colonizer.fuel, Double::sum);
        w.ships.remove(colonizer); // colonizer is consumed
    }

    private static void applyDispatchShip(World w, DispatchShipCommand ds) {
        Ship s = w.findShip(ds.shipId());
        if (s == null) throw new CommandRejectedException("No such ship: " + ds.shipId());
        if (s.orbitingBodyId != null && s.shipClass != ShipClass.EXPLORER) {
            Body at = w.findBody(s.orbitingBodyId);
            throw new CommandRejectedException("Ship is orbiting " + (at != null ? at.name : s.orbitingBodyId)
                + "; found a colony or retire it");
        }
        if (s.state != ShipState.IDLE) throw new CommandRejectedException("Ship not idle: " + ds.shipId());
        String destBodyId;
        if (ds.destSiteId() != null) {
            Site destSite = w.findSite(ds.destSiteId());
            if (destSite == null) throw new CommandRejectedException("No such dest: " + ds.destSiteId());
            destBodyId = destSite.bodyId;
        } else {
            if (w.findBody(ds.destBodyId()) == null)
                throw new CommandRejectedException("No such body: " + ds.destBodyId());
            if (s.shipClass != ShipClass.COLONIZER && s.shipClass != ShipClass.EXPLORER)
                throw new CommandRejectedException("Only colonizers and explorers can travel to a body without a site");
            destBodyId = ds.destBodyId();
        }
        String overflow = cargoOverflow(s, ds.manifest());
        if (overflow != null) throw new CommandRejectedException(overflow);
        if (s.orbitingBodyId != null) {
            // An explorer in orbit has nowhere to load; it leaves now on its own tank.
            String why = TransitPhase.departFromOrbit(w, s, ds.destSiteId(), ds.destBodyId());
            if (why != null) throw new CommandRejectedException(why);
            return;
        }
        // Spec §3.7: reject up front if the ship clearly can't afford the trip. The
        // departure-time check still runs later, but this saves N ticks of LOADING.
        String shortfall = fuelShortfall(w, s, destBodyId, ds.manifest());
        if (shortfall != null) throw new CommandRejectedException(shortfall);
        // Move into LOADING; transit math runs in loadingAndUnloading() when manifest is filled.
        // Stash dest + manifest on Transit with PENDING_ARRIVAL_TICK; loadingAndUnloading()
        // recomputes the real arrival tick at departure.
        s.state = ShipState.LOADING;
        s.transit = new Transit(s.currentSiteId, ds.destSiteId(), ds.destBodyId(), w.tick,
                                Transit.PENDING_ARRIVAL_TICK, Transit.snapshot(ds.manifest()));
    }

    /** Why {@code s} can't hold {@code manifest}, or null if it fits. */
    public static String cargoOverflow(Ship s, java.util.Map<Resource, Double> manifest) {
        double total = 0.0;
        for (Double v : manifest.values()) if (v != null && v > 0) total += v;
        double cap = s.shipClass.cargoCap();
        if (total <= cap + 1e-9) return null;
        return String.format("Manifest of %.0f is more than %s can carry (%.0f)", total, s.name, cap);
    }

    /**
     * Why {@code s} can't afford a trip from its site to {@code destBodyId} carrying
     * {@code manifest}, estimated from current positions, or null if it can (or isn't at a
     * site). The dispatch dialog calls this too, so the player sees the problem right away.
     */
    public static String fuelShortfall(World w, Ship s, String destBodyId,
                                       java.util.Map<Resource, Double> manifest) {
        Site originSite = w.findSite(s.currentSiteId);
        if (originSite == null) {
            // In orbit, an explorer flies on what's in its tank.
            if (s.orbitingBodyId == null || destBodyId == null) return null;
            double[] op = OrbitalGeometry.bodyPosition(w, s.orbitingBodyId, w.tick);
            double[] dp = OrbitalGeometry.bodyPosition(w, destBodyId, w.tick);
            double dist = s.orbitingBodyId.equals(destBodyId) ? 0.0 : Math.hypot(dp[0] - op[0], dp[1] - op[1]);
            double est = FUEL_K * s.shipClass.dryMass() * dist * TechEffects.fuelCostMultiplier(w.tech);
            return s.fuel + 1e-9 >= est ? null : TransitPhase.tankShortfall(s, est);
        }
        double[] op = OrbitalGeometry.bodyPosition(w, originSite.bodyId, w.tick);
        double[] dp = OrbitalGeometry.bodyPosition(w, destBodyId, w.tick);
        double dx = dp[0] - op[0], dy = dp[1] - op[1];
        double dist = Math.sqrt(dx * dx + dy * dy);
        double manifestMass = 0.0;
        for (Double v : manifest.values()) if (v != null) manifestMass += v;
        double estCost = FUEL_K * (s.shipClass.dryMass() + manifestMass) * dist
                       * TechEffects.fuelCostMultiplier(w.tech);
        // The ship tops up from the origin's FUEL at departure, after loading any FUEL cargo.
        double originFuel = originSite.stockpile.getOrDefault(Resource.FUEL, 0.0);
        Double mf = manifest.get(Resource.FUEL);
        double manifestFuel = mf == null ? 0.0 : mf;
        double available = s.fuel + Math.max(0.0, originFuel - manifestFuel);
        if (available >= estCost) return null;
        return String.format("Not enough fuel at %s for this trip: need ≈%.0f, have %.0f",
                             originSite.name, estCost, available);
    }

    private static String currentBodyOf(World w, Ship s) {
        if (s.orbitingBodyId != null) return s.orbitingBodyId;
        if (s.currentSiteId == null) return null;
        Site site = w.findSite(s.currentSiteId);
        return site == null ? null : site.bodyId;
    }

    static class CommandRejectedException extends RuntimeException {
        CommandRejectedException(String m) { super(m); }
    }
}
