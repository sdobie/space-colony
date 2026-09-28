package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.RetireShipCommand;
import spacecolony.sim.phases.TransitPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 6 §6.2: colonizers fly to a body with no site, wait in orbit, and found a colony there. */
class ColonizeTest {
    private static World withColonizer() {
        World w = WorldGenerator.generate(1L);
        w.findSite("site-earth-hub").stockpile.put(Resource.FUEL, 1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
        sim.advance(w);
        return w;
    }

    private static boolean rejected(World w, String prefix) {
        return w.recentEvents.stream().anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED
            && e.message().startsWith(prefix));
    }

    /** Colonizer c1 with FOOD 40 aboard, orbiting Mars. */
    private static World orbitingMars(Simulator sim) {
        World w = withColonizer();
        sim.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of(Resource.FOOD, 40.0)));
        for (int i = 0; i < 50 && w.findShip("c1").orbitingBodyId == null; i++) sim.advance(w);
        return w;
    }

    @Test void colonizer_toBody_departsForMars() {
        World w = withColonizer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of(Resource.FOOD, 40.0)));
        for (int i = 0; i < 5 && w.findShip("c1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship c = w.findShip("c1");
        assertEquals(ShipState.IN_TRANSIT, c.state);
        assertNull(c.transit.destSiteId());
        assertEquals("mars", c.transit.destBodyId());
        assertTrue(c.transit.arrivalTick() > c.transit.departureTick());
    }

    @Test void hauler_toBody_rejected() {
        World w = withColonizer();
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("h1", "H1", ShipClass.HAULER, "site-earth-hub"));
        sim.enqueue(DispatchShipCommand.toBody("h1", "mars", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "Only colonizers can travel to a body without a site"));
        assertEquals(ShipState.IDLE, w.findShip("h1").state);
    }

    @Test void unknownBody_rejected() {
        World w = withColonizer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("c1", "pluto", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "No such body: pluto"));
    }

    @Test void dispatchCommand_requiresExactlyOneDestination() {
        assertThrows(IllegalArgumentException.class,
            () -> new DispatchShipCommand("c1", "site-earth-hub", "mars", Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new DispatchShipCommand("c1", null, null, Map.of()));
    }

    @Test void colonizer_arrives_orbitingMars_withCargoAboard() {
        World w = orbitingMars(new Simulator());
        Ship c = w.findShip("c1");
        assertEquals(ShipState.IDLE, c.state);
        assertEquals("mars", c.orbitingBodyId);
        assertNull(c.currentSiteId);
        assertNull(c.transit);
        assertEquals(40.0, c.cargo.get(Resource.FOOD), 1e-6);
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().contains("is orbiting Mars")));
    }

    @Test void orbitingColonizer_cannotBeDispatched() {
        Simulator sim = new Simulator();
        World w = orbitingMars(sim);
        sim.enqueue(new DispatchShipCommand("c1", "site-earth-hub", Map.of()));
        sim.advance(w);
        assertTrue(rejected(w, "Ship is orbiting Mars; found a colony or retire it"));
        assertEquals("mars", w.findShip("c1").orbitingBodyId);
    }

    @Test void orbitingColonizer_canBeRetired() {
        Simulator sim = new Simulator();
        World w = orbitingMars(sim);
        sim.enqueue(new RetireShipCommand("c1"));
        sim.advance(w);
        assertNull(w.findShip("c1"));
    }

    @Test void foundColonyFromOrbit_createsSiteWithCargo_andSettlesMars() {
        Simulator sim = new Simulator();
        World w = orbitingMars(sim);
        sim.enqueue(new BuildSiteCommand("site-mars-a", "Ares", "mars", 0.3, 1.2, "c1"));
        sim.advance(w);
        Site ares = w.findSite("site-mars-a");
        assertNotNull(ares);
        assertEquals("mars", ares.bodyId);
        assertEquals(1, ares.buildings.size());
        assertEquals(BuildingType.HABITAT, ares.buildings.get(0).type);
        assertNull(w.findShip("c1"));
        // 40 FOOD arrived; population 0 eats nothing on the founding tick.
        assertEquals(40.0, ares.stockpile.getOrDefault(Resource.FOOD, 0.0), 1.0);
        sim.advance(w);
        assertTrue(w.goals.achieved.contains("first-mars-colony"));
    }

    /** Regression: on the standard start (no extra FUEL) a colonizer could not afford Mars. */
    @Test void standardStart_colonizerWithFullHold_departsForMars() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
        sim.advance(w);
        sim.enqueue(DispatchShipCommand.toBody("c1", "mars",
            Map.of(Resource.FOOD, 40.0, Resource.WATER, 40.0, Resource.METAL, 20.0)));
        for (int i = 0; i < 5 && w.findShip("c1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, w.findShip("c1").state);
    }

    /** Earth Hub's starting FUEL covers an Earth→Mars colonizer run with a full hold at any alignment. */
    @Test void standardStart_fuelCoversWorstCaseMarsRun() {
        World w = WorldGenerator.generate(1L);
        double stock = w.findSite("site-earth-hub").stockpile.get(Resource.FUEL);
        for (long t = 0; t < 800; t++) {
            double cost = TransitPhase.fuelCostToBody(w, ShipClass.COLONIZER, ShipClass.COLONIZER.cargoCap(),
                "site-earth-hub", "mars", t, t + 8);
            assertTrue(cost <= stock, "t=" + t + " costs " + cost + " > " + stock);
        }
    }
}
