package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.Command;

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

    private void apply(World w, Command c) {
        if (c instanceof spacecolony.sim.commands.BuildBuildingCommand bb) {
            Site s = w.findSite(bb.siteId());
            if (s == null) throw new CommandRejectedException("No such site: " + bb.siteId());
            s.buildings.add(new Building(bb.type(), 1));
        } else if (c instanceof spacecolony.sim.commands.BuildShipCommand bs) {
            Site s = w.findSite(bs.shipyardSiteId());
            if (s == null) throw new CommandRejectedException("No such site: " + bs.shipyardSiteId());
            boolean hasYard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.enabled);
            if (!hasYard) throw new CommandRejectedException("Site has no shipyard: " + bs.shipyardSiteId());
            if (w.findShip(bs.shipId()) != null) throw new CommandRejectedException("Ship id taken: " + bs.shipId());
            w.ships.add(new Ship(bs.shipId(), bs.name(), bs.shipClass(), bs.shipyardSiteId()));
        } else if (c instanceof spacecolony.sim.commands.RetireShipCommand rs) {
            Ship s = w.findShip(rs.shipId());
            if (s == null) throw new CommandRejectedException("No such ship: " + rs.shipId());
            if (s.state == ShipState.IN_TRANSIT) throw new CommandRejectedException("Cannot retire ship in transit");
            w.ships.remove(s);
        } else if (c instanceof spacecolony.sim.commands.QueueResearchCommand qr) {
            if (TechCatalog.get(qr.techId()) == null) throw new CommandRejectedException("Unknown tech: " + qr.techId());
            if (w.tech.researched.contains(qr.techId())) throw new CommandRejectedException("Already researched: " + qr.techId());
            w.tech.activeId = qr.techId();
            w.tech.accumulatedPoints = 0.0;
        } else if (c instanceof spacecolony.sim.commands.BuildSiteCommand bsc) {
            Body body = w.findBody(bsc.bodyId());
            if (body == null) throw new CommandRejectedException("No such body: " + bsc.bodyId());
            Ship colonizer = w.findShip(bsc.colonizerShipId());
            if (colonizer == null || colonizer.shipClass != ShipClass.COLONIZER)
                throw new CommandRejectedException("Need a COLONIZER ship at the body");
            if (!body.id.equals(currentBodyOf(w, colonizer)))
                throw new CommandRejectedException("Colonizer not at target body");
            Site s = new Site(bsc.siteId(), bsc.name(), bsc.bodyId(), bsc.lat(), bsc.lon(), 100);
            s.buildings.add(new Building(BuildingType.HABITAT, 1));
            body.sites.add(s);
            w.ships.remove(colonizer); // colonizer is consumed
        } else if (c instanceof spacecolony.sim.commands.DispatchShipCommand ds) {
            Ship s = w.findShip(ds.shipId());
            if (s == null) throw new CommandRejectedException("No such ship: " + ds.shipId());
            if (s.state != ShipState.IDLE) throw new CommandRejectedException("Ship not idle: " + ds.shipId());
            if (w.findSite(ds.destSiteId()) == null) throw new CommandRejectedException("No such dest: " + ds.destSiteId());
            // Move into LOADING; transit math runs in loadingAndUnloading() when manifest is filled.
            s.state = ShipState.LOADING;
            // Stash dest + manifest on a one-shot Transit holder. We use Transit's destSiteId
            // and cargoSnapshot fields with a sentinel arrival tick of -1 to mean "still loading".
            s.transit = new Transit(s.currentSiteId, ds.destSiteId(), w.tick, -1L, Transit.snapshot(ds.manifest()));
        }
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
