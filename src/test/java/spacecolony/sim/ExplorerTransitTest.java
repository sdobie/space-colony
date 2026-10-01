package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.phases.TransitPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §3.1 and §3.4: the explorer's tank and flights from orbit. */
class ExplorerTransitTest {
    static final String HUB = "site-earth-hub";

    /** Seed 1, explorer x1 ("Scout-1") idle at Earth Hub with 1000 FUEL in stock. */
    static World withExplorer() {
        World w = WorldGenerator.generate(1L);
        w.findSite(HUB).stockpile.put(Resource.FUEL, 1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("x1", "Scout-1", ShipClass.EXPLORER, HUB));
        sim.advance(w);
        return w;
    }

    private static boolean rejected(World w, String prefix) {
        return w.recentEvents.stream().anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED
            && e.message().startsWith(prefix));
    }

    private static World orbiting(String bodyId) {
        World w = withExplorer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", bodyId, Map.of()));
        for (int i = 0; i < 80 && w.findShip("x1").orbitingBodyId == null; i++) sim.advance(w);
        assertEquals(bodyId, w.findShip("x1").orbitingBodyId);
        return w;
    }

    @Test void explorerClass() {
        assertEquals(20.0, ShipClass.EXPLORER.dryMass());
        assertEquals(0.0, ShipClass.EXPLORER.cargoCap());
        assertEquals(0.8, ShipClass.EXPLORER.speed());
        assertEquals(100.0, ShipClass.EXPLORER.tankCap());
        for (ShipClass c : ShipClass.values()) if (c != ShipClass.EXPLORER) assertEquals(0.0, c.tankCap());
    }

    @Test void leavingAColony_fillsTheTank() {
        World w = withExplorer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "mars", Map.of()));
        for (int i = 0; i < 5 && w.findShip("x1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship x = w.findShip("x1");
        assertEquals(ShipState.IN_TRANSIT, x.state);
        double cost = TransitPhase.fuelCostToBody(w, ShipClass.EXPLORER, 0.0, HUB, "mars",
            x.transit.departureTick(), x.transit.arrivalTick());
        assertTrue(cost > 0);
        assertEquals(100.0 - cost, x.fuel, 1e-9);
        // The hub paid for exactly one full tank, this trip included.
        assertEquals(1_000.0 - 100.0, w.findSite(HUB).stockpile.get(Resource.FUEL), 1e-6);
    }

    @Test void shortStock_leavesWithWhatThereIs() {
        World w = withExplorer();
        Site hub = w.findSite(HUB);
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "mars", Map.of()));
        hub.stockpile.put(Resource.FUEL, 30.0);
        for (int i = 0; i < 5 && w.findShip("x1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship x = w.findShip("x1");
        assertEquals(ShipState.IN_TRANSIT, x.state);
        double cost = TransitPhase.fuelCostToBody(w, ShipClass.EXPLORER, 0.0, HUB, "mars",
            x.transit.departureTick(), x.transit.arrivalTick());
        assertTrue(cost < 30.0, "trip " + cost);
        assertEquals(30.0 - cost, x.fuel, 1e-9);
        assertEquals(0.0, hub.stockpile.get(Resource.FUEL), 1e-9);
    }

    @Test void hopsFromOrbit_onItsTank() {
        World w = orbiting("mars");
        Ship x = w.findShip("x1");
        double before = x.fuel;
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "belt-a", Map.of()));
        sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, x.state);
        assertNull(x.orbitingBodyId);
        assertNull(x.transit.originSiteId());
        assertEquals("mars", x.transit.originBodyId());
        assertEquals("mars", x.transit.originBody(w));
        double cost = TransitPhase.fuelCostBodies(w, ShipClass.EXPLORER, 0.0, "mars", "belt-a",
            x.transit.departureTick(), x.transit.arrivalTick());
        assertEquals(before - cost, x.fuel, 1e-9);
        for (int i = 0; i < 80 && x.orbitingBodyId == null; i++) sim.advance(w);
        assertEquals("belt-a", x.orbitingBodyId);
        assertTrue(w.isSurveyed("belt-a"));
    }

    @Test void hopFromOrbit_leavesOnTheTickBeingComputed() {
        // Colony departures leave in the DEPARTURES phase of the tick being computed; a hop
        // from orbit must too, or a one-day moon hop lands in the same tick it was ordered.
        World w = orbiting("jovian");
        Ship x = w.findShip("x1");
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "io", Map.of()));
        sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, x.state, "a one-day hop is still under way after the tick it was ordered");
        assertNull(x.orbitingBodyId);
        assertEquals(w.tick, x.transit.departureTick());
        assertTrue(x.transit.arrivalTick() > w.tick);
        Event departed = w.recentEvents.stream().filter(e -> e.kind() == EventKind.SHIP_DEPARTED)
            .reduce((a, b) -> b).orElseThrow();
        assertEquals(w.tick, departed.tick());
        sim.advance(w);
        assertEquals("io", x.orbitingBodyId);
        assertTrue(w.isSurveyed("io"));
    }

    @Test void hopItCantAfford_isRejectedWithTheTankMessage() {
        World w = orbiting("mars");
        Ship x = w.findShip("x1");
        x.fuel = 0.5;
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "jovian", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "Not enough fuel in Scout-1's tank"));
        assertEquals("mars", x.orbitingBodyId);
        assertEquals(ShipState.IDLE, x.state);
    }

    @Test void fromOrbit_toAColony_docksIdleWithItsTank() {
        World w = orbiting("mars");
        Ship x = w.findShip("x1");
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("x1", HUB, Map.of()));
        sim.advance(w);
        double left = x.fuel;
        for (int i = 0; i < 80 && !(x.state == ShipState.IDLE && HUB.equals(x.currentSiteId)); i++) sim.advance(w);
        assertEquals(ShipState.IDLE, x.state);
        assertEquals(HUB, x.currentSiteId);
        assertEquals(left, x.fuel, 1e-9);
    }

    @Test void orbitingColonizer_stillCantBeDispatched() {
        World w = WorldGenerator.generate(1L);
        Ship c = new Ship("c1", "Ark", ShipClass.COLONIZER, null);
        c.orbitingBodyId = "mars";
        c.fuel = 500;
        w.ships.add(c);
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("c1", "belt-a", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "Ship is orbiting Mars; found a colony or retire it"));
    }

    @Test void explorerManifest_isRejected() {
        World w = withExplorer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("x1", "mars", Map.of(Resource.ORE, 1.0)));
        sim.advance(w);
        assertTrue(rejected(w, "Manifest of 1 is more than Scout-1 can carry"));
    }

    @Test void haulerStillCantGoToABody() {
        World w = withExplorer();
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("h1", "H1", ShipClass.HAULER, HUB));
        sim.enqueue(DispatchShipCommand.toBody("h1", "mars", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "Only colonizers and explorers can travel to a body without a site"));
    }
}
