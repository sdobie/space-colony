package spacecolony.sim;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.RetireShipCommand;
import spacecolony.sim.phases.CommandPhase;
import spacecolony.sim.phases.TransitPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 10 §4: hydrogen and synfuel, burned by fuel value, hydrogen first. */
class FuelsTest {
    private static final String HUB = "site-earth-hub";

    private static World world(double hydrogen, double synfuel) {
        World w = WorldGenerator.generate(1L);
        w.findBody("mars").sites.add(new Site("site-mars-1", "Mars 1", "mars", 0, 0, 100));
        Site hub = w.findSite(HUB);
        hub.stockpile.put(Resource.HYDROGEN, hydrogen);
        hub.stockpile.put(Resource.SYNFUEL, synfuel);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("h1", "H1", ShipClass.HAULER, HUB));
        sim.advance(w);
        TestShips.finishBuilds(w);
        TestShips.finishBuilds(w);
        return w;
    }

    private static Ship depart(World w) {
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 10.0)));
        for (int i = 0; i < 10 && w.findShip("h1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        return w.findShip("h1");
    }

    private static double tripCost(World w, Ship h) {
        return TransitPhase.fuelCost(w, ShipClass.HAULER, h.cargoMass(), HUB, "site-mars-1",
            h.transit.departureTick(), h.transit.arrivalTick());
    }

    @Test void values() {
        assertEquals(1.0, Fuels.value(Resource.HYDROGEN));
        assertEquals(0.5, Fuels.value(Resource.SYNFUEL));
        assertEquals(0.0, Fuels.value(Resource.ORE));
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        m.put(Resource.HYDROGEN, 40.0);
        m.put(Resource.SYNFUEL, 48.0);
        assertEquals(64.0, Fuels.valueOf(m), 1e-12);
        assertEquals("40 hydrogen, 48 synfuel", Fuels.describe(m));
    }

    @Test void synfuelOnly_burnsTwiceTheUnits() {
        World w = world(0.0, 900.0);
        Ship h = depart(w);
        assertEquals(ShipState.IN_TRANSIT, h.state);
        double cost = tripCost(w, h);
        assertEquals(900.0 - 2 * cost, w.findSite(HUB).stockpile.get(Resource.SYNFUEL), 1e-6);
        assertEquals(0.0, h.fuelValue(), 1e-6);
    }

    @Test void mixedStock_usesHydrogenFirst() {
        World w = world(10.0, 900.0);
        Ship h = depart(w);
        double cost = tripCost(w, h);
        assertTrue(cost > 10.0);
        Site hub = w.findSite(HUB);
        assertEquals(0.0, hub.stockpile.get(Resource.HYDROGEN), 1e-9);
        assertEquals(900.0 - 2 * (cost - 10.0), hub.stockpile.get(Resource.SYNFUEL), 1e-6);
    }

    @Test void burn_takesHydrogenFirst() {
        Map<Resource, Double> tank = new EnumMap<>(Resource.class);
        tank.put(Resource.HYDROGEN, 10.0);
        tank.put(Resource.SYNFUEL, 30.0);
        Fuels.burn(tank, 15.0);
        assertEquals(0.0, tank.get(Resource.HYDROGEN), 1e-12);
        assertEquals(20.0, tank.get(Resource.SYNFUEL), 1e-12);
    }

    @Test void retire_drainsEachFuel() {
        World w = world(0.0, 0.0);
        Ship h = w.findShip("h1");
        h.setTank(Resource.HYDROGEN, 7.0);
        h.setTank(Resource.SYNFUEL, 9.0);
        Simulator sim = new Simulator();
        sim.enqueue(new RetireShipCommand("h1"));
        sim.advance(w);
        assertEquals(7.0, w.findSite(HUB).stockpile.get(Resource.HYDROGEN), 1e-6);
        assertEquals(9.0, w.findSite(HUB).stockpile.get(Resource.SYNFUEL), 1e-6);
    }

    @Test void shortfall_namesBothFuels() {
        World w = world(1.0, 2.0);
        String why = CommandPhase.fuelShortfall(w, w.findShip("h1"), "mars", Map.of());
        assertNotNull(why);
        assertTrue(why.startsWith("Not enough fuel at Earth Hub for this trip: need ≈"), why);
        assertTrue(why.endsWith("have 2 (1 hydrogen, 2 synfuel)"), why);
    }

    @Test void explorerTank_fillsToHundredValue_withSynfuel() {
        World w = WorldGenerator.generate(1L);
        w.findBody("mars").sites.add(new Site("site-mars-1", "Mars 1", "mars", 0, 0, 100));
        Site hub = w.findSite(HUB);
        hub.stockpile.put(Resource.HYDROGEN, 0.0);
        hub.stockpile.put(Resource.SYNFUEL, 1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("x1", "X1", ShipClass.EXPLORER, HUB));
        sim.advance(w);
        TestShips.finishBuilds(w);
        TestShips.finishBuilds(w);
        sim.enqueue(new DispatchShipCommand("x1", "site-mars-1", Map.of()));
        for (int i = 0; i < 10 && w.findShip("x1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship x = w.findShip("x1");
        double cost = TransitPhase.fuelCost(w, ShipClass.EXPLORER, 0, HUB, "site-mars-1",
            x.transit.departureTick(), x.transit.arrivalTick());
        assertEquals(100.0 - cost, x.fuelValue(), 1e-6);
        assertEquals(1_000.0 - 200.0, hub.stockpile.get(Resource.SYNFUEL), 1e-6);
    }
}
