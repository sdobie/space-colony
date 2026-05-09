package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.Command;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.sim.commands.RetireShipCommand;

/**
 * Pure simulation engine. Advances {@link World} by one tick at a time. Player intent
 * enters via the command queue; UI never mutates World directly.
 *
 * Per-tick phases (run in this order):
 *   1. Drain command queue
 *   2. Advance tick + orbits (positions are computed from tick on demand)
 *   3. Advance ships in transit (arrivals)
 *   4. Per-site production & consumption
 *   5. Loading / unloading ships (departures)
 *   6. Random events
 *   7. Research progress
 *   8. Goal check
 */
public class Simulator {
    private final Deque<Command> commandQueue = new ArrayDeque<>();

    public void enqueue(Command c) { commandQueue.addLast(c); }

    public void advance(World w) {
        drainCommands(w);
        w.tick++;
        advanceTransits(w);
        productionAndConsumption(w);
        loadingAndUnloading(w);
        randomEvents(w);
        researchProgress(w);
        goalCheck(w);
    }

    private void drainCommands(World w) {
        while (!commandQueue.isEmpty()) {
            Command c = commandQueue.removeFirst();
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
    private void apply(World w, Command c) {
        switch (c) {
            case BuildBuildingCommand bb -> applyBuildBuilding(w, bb);
            case BuildShipCommand bs     -> applyBuildShip(w, bs);
            case RetireShipCommand rs    -> applyRetireShip(w, rs);
            case QueueResearchCommand qr -> applyQueueResearch(w, qr);
            case BuildSiteCommand bsc    -> applyBuildSite(w, bsc);
            case DispatchShipCommand ds  -> applyDispatchShip(w, ds);
        }
    }

    private void applyBuildBuilding(World w, BuildBuildingCommand bb) {
        Site s = w.findSite(bb.siteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bb.siteId());
        s.buildings.add(new Building(bb.type(), 1));
    }

    private void applyBuildShip(World w, BuildShipCommand bs) {
        Site s = w.findSite(bs.shipyardSiteId());
        if (s == null) throw new CommandRejectedException("No such site: " + bs.shipyardSiteId());
        boolean hasYard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.enabled);
        if (!hasYard) throw new CommandRejectedException("Site has no shipyard: " + bs.shipyardSiteId());
        if (w.findShip(bs.shipId()) != null) throw new CommandRejectedException("Ship id taken: " + bs.shipId());
        w.ships.add(new Ship(bs.shipId(), bs.name(), bs.shipClass(), bs.shipyardSiteId()));
    }

    private void applyRetireShip(World w, RetireShipCommand rs) {
        Ship s = w.findShip(rs.shipId());
        if (s == null) throw new CommandRejectedException("No such ship: " + rs.shipId());
        if (s.state == ShipState.IN_TRANSIT) throw new CommandRejectedException("Cannot retire ship in transit");
        w.ships.remove(s);
    }

    private void applyQueueResearch(World w, QueueResearchCommand qr) {
        if (TechCatalog.get(qr.techId()) == null) throw new CommandRejectedException("Unknown tech: " + qr.techId());
        if (w.tech.researched.contains(qr.techId())) throw new CommandRejectedException("Already researched: " + qr.techId());
        w.tech.activeId = qr.techId();
        w.tech.accumulatedPoints = 0.0;
    }

    private void applyBuildSite(World w, BuildSiteCommand bsc) {
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

    private void applyDispatchShip(World w, DispatchShipCommand ds) {
        Ship s = w.findShip(ds.shipId());
        if (s == null) throw new CommandRejectedException("No such ship: " + ds.shipId());
        if (s.state != ShipState.IDLE) throw new CommandRejectedException("Ship not idle: " + ds.shipId());
        if (w.findSite(ds.destSiteId()) == null) throw new CommandRejectedException("No such dest: " + ds.destSiteId());
        // Move into LOADING; transit math runs in loadingAndUnloading() when manifest is filled.
        // Stash dest + manifest on Transit with PENDING_ARRIVAL_TICK; loadingAndUnloading()
        // recomputes the real arrival tick at departure.
        s.state = ShipState.LOADING;
        s.transit = new Transit(s.currentSiteId, ds.destSiteId(), w.tick,
                                Transit.PENDING_ARRIVAL_TICK, Transit.snapshot(ds.manifest()));
    }

    private String currentBodyOf(World w, Ship s) {
        if (s.currentSiteId == null) return null;
        Site site = w.findSite(s.currentSiteId);
        return site == null ? null : site.bodyId;
    }

    private static class CommandRejectedException extends RuntimeException {
        CommandRejectedException(String m) { super(m); }
    }

    private void advanceTransits(World w)       { /* Task 21 */ }
    private void productionAndConsumption(World w) { /* Task 22 */ }
    private void loadingAndUnloading(World w)   { /* Task 21 */ }
    private void randomEvents(World w)          { /* Task 23 */ }
    private void researchProgress(World w)      { /* Task 24 */ }
    private void goalCheck(World w)             { /* Task 25 */ }
}
