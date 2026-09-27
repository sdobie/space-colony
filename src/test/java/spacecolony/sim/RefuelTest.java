package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.phases.TransitPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 6 §6.1: departures draw the trip's fuel shortfall from the origin's FUEL stock. */
class RefuelTest {
    private static final String HUB = "site-earth-hub";

    /** Earth Hub with a fresh hauler, and a Mars site to fly to. */
    private static World world(double hubFuel) {
        World w = WorldGenerator.generate(1L);
        w.findBody("mars").sites.add(new Site("site-mars-1", "Mars 1", "mars", 0, 0, 100));
        w.findSite(HUB).stockpile.put(Resource.FUEL, hubFuel);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("h1", "H1", ShipClass.HAULER, HUB));
        sim.advance(w);
        return w;
    }

    private static Ship departed(World w, Simulator sim) {
        for (int i = 0; i < 10 && w.findShip("h1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        return w.findShip("h1");
    }

    private static double tripCost(World w, Ship h) {
        return TransitPhase.fuelCost(w, ShipClass.HAULER, h.cargoMass(), HUB, "site-mars-1",
            h.transit.departureTick(), h.transit.arrivalTick());
    }

    @Test void newShip_departs_drawingExactlyTheCostFromOrigin() {
        World w = world(1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 10.0)));
        Ship h = departed(w, sim);
        assertEquals(ShipState.IN_TRANSIT, h.state);
        double cost = tripCost(w, h);
        assertTrue(cost > 0);
        assertEquals(1_000.0 - cost, w.findSite(HUB).stockpile.get(Resource.FUEL), 1e-6);
        assertEquals(0.0, h.fuel, 1e-6);
    }

    @Test void shipWithEnoughFuel_drawsNothing() {
        World w = world(1_000.0);
        w.findShip("h1").fuel = 10_000.0;
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 10.0)));
        Ship h = departed(w, sim);
        assertEquals(ShipState.IN_TRANSIT, h.state);
        assertEquals(1_000.0, w.findSite(HUB).stockpile.get(Resource.FUEL), 1e-9);
        assertEquals(10_000.0 - tripCost(w, h), h.fuel, 1e-6);
    }

    @Test void originShort_rejectedAtCommandTime() {
        World w = world(1.0);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 10.0)));
        sim.advance(w);
        assertEquals(ShipState.IDLE, w.findShip("h1").state);
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED
            && e.message().startsWith("Not enough fuel at Earth Hub for this trip")),
            "expected a not-enough-fuel rejection");
    }

    @Test void manifestFuel_isCargo_loadedBeforeTheDraw() {
        World w = world(900.0);   // under the hub's stockpile cap
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.FUEL, 100.0)));
        Ship h = departed(w, sim);
        assertEquals(ShipState.IN_TRANSIT, h.state);
        assertEquals(100.0, h.cargo.get(Resource.FUEL), 1e-6);
        assertEquals(900.0 - 100.0 - tripCost(w, h), w.findSite(HUB).stockpile.get(Resource.FUEL), 1e-6);
    }
}
