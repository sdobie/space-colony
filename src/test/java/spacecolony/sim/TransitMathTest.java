package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class TransitMathTest {

    private static World worldWithHaulerAtEarth() {
        World w = WorldGenerator.generate(1L);
        Ship s = new Ship("ship-h1", "H1", ShipClass.HAULER, "site-earth-hub");
        s.fuel = 1_000_000.0;
        w.ships.add(s);
        // Stock the site with metal so the manifest can fill.
        w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
        return w;
    }

    @Test
    void dispatchedShip_loadsThenDeparts() {
        World w = worldWithHaulerAtEarth();
        Site mars = createDummySiteAtMars(w);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", mars.id, Map.of(Resource.METAL, 50.0)));
        sim.advance(w); // command applied, ship -> LOADING
        Ship s = w.findShip("ship-h1");
        assertEquals(ShipState.LOADING, s.state);
        // Run more ticks until manifest is filled and ship departs.
        for (int i = 0; i < 50 && s.state == ShipState.LOADING; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, s.state);
        assertTrue(s.transit.arrivalTick() > w.tick);
        assertEquals(50.0, s.cargo.get(Resource.METAL), 1e-9);
        assertTrue(s.fuel < 1_000_000.0, "fuel should be deducted at departure");
    }

    @Test
    void shipReachingArrivalTick_unloadsAtDestination() {
        World w = worldWithHaulerAtEarth();
        Site mars = createDummySiteAtMars(w);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", mars.id, Map.of(Resource.METAL, 50.0)));
        for (int i = 0; i < 5000; i++) {
            sim.advance(w);
            Ship s = w.findShip("ship-h1");
            if (s.state == ShipState.IDLE && i > 50) break;
        }
        Ship s = w.findShip("ship-h1");
        assertEquals(ShipState.IDLE, s.state);
        assertEquals(mars.id, s.currentSiteId);
        assertEquals(0.0, s.cargo.get(Resource.METAL), 1e-9);
        assertEquals(50.0, mars.stockpile.get(Resource.METAL), 1e-9);
    }

    @Test
    void insufficientFuel_rejectsDispatch() {
        World w = worldWithHaulerAtEarth();
        Ship s = w.findShip("ship-h1");
        s.fuel = 0.0;
        Site mars = createDummySiteAtMars(w);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", mars.id, Map.of(Resource.METAL, 50.0)));
        sim.advance(w);
        // Either the dispatch was rejected up front, or it'll be aborted at departure.
        // We accept either: just assert ship is not IN_TRANSIT.
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertNotEquals(ShipState.IN_TRANSIT, w.findShip("ship-h1").state);
        assertTrue(w.recentEvents.stream()
            .anyMatch(e -> e.kind() == EventKind.SHIP_OUT_OF_FUEL || e.kind() == EventKind.COMMAND_REJECTED));
    }

    private static Site createDummySiteAtMars(World w) {
        Site mars = new Site("site-mars-test", "Mars Test", "mars", 0.0, 0.0, 50);
        mars.population = 0;
        w.findBody("mars").sites.add(mars);
        return mars;
    }

    @Test
    void shipDispatchedToJovianMoon_arrivesViaParentRecursion() {
        // Verifies bodyPosition()'s moon-parent recursion: dispatching to Europa
        // (which orbits Jovian) must use Jovian's heliocentric position plus
        // Europa's local orbit. A non-recursive bodyPosition would put Europa
        // at <0.004 AU from the sun and the trip would never converge.
        World w = worldWithHaulerAtEarth();
        Site moon = new Site("site-europa-test", "Europa Test", "europa", 0.0, 0.0, 50);
        moon.population = 0;
        w.findBody("europa").sites.add(moon);

        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", moon.id, Map.of(Resource.METAL, 30.0)));
        for (int i = 0; i < 20000; i++) {
            sim.advance(w);
            Ship s = w.findShip("ship-h1");
            if (s.state == ShipState.IDLE && i > 50) break;
        }
        Ship s = w.findShip("ship-h1");
        assertEquals(ShipState.IDLE, s.state, "Ship should reach Europa");
        assertEquals(moon.id, s.currentSiteId);
        assertEquals(30.0, moon.stockpile.get(Resource.METAL), 1e-9);
    }
}
