package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class UnloadCapTest {
    /** A ship docked UNLOADING at the hub with {@code ice} ICE aboard. */
    private static Ship unloadingAtHub(World w, double ice) {
        Ship s = new Ship("unloader", "Unloader", ShipClass.HAULER, "site-earth-hub");
        s.state = ShipState.UNLOADING;
        s.transit = new Transit("site-earth-hub", "site-earth-hub", 0, 1, java.util.Map.of(Resource.ICE, ice));
        s.cargo.put(Resource.ICE, ice);
        w.ships.add(s);
        return s;
    }

    @Test
    void unloading_stopsAtCap_andKeepsTheRestAboard() {
        // Nothing at the starting hub produces or consumes ICE, so the numbers are exact.
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite("site-earth-hub");
        double cap = hub.stockpileCap.get(Resource.ICE);
        hub.stockpile.put(Resource.ICE, cap - 5.0);
        Ship s = unloadingAtHub(w, 80.0);

        new Simulator().advance(w);

        assertEquals(cap, hub.stockpile.get(Resource.ICE), 1e-9);
        assertEquals(75.0, s.cargo.get(Resource.ICE), 1e-9);
        assertEquals(ShipState.UNLOADING, s.state, "ship waits for room instead of dumping cargo");
    }

    @Test
    void unloading_resumesOnceThereIsRoom() {
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite("site-earth-hub");
        double cap = hub.stockpileCap.get(Resource.ICE);
        hub.stockpile.put(Resource.ICE, cap);
        Ship s = unloadingAtHub(w, 40.0);
        Simulator sim = new Simulator();

        sim.advance(w);
        assertEquals(40.0, s.cargo.get(Resource.ICE), 1e-9);

        hub.stockpile.put(Resource.ICE, 0.0);
        sim.advance(w);
        sim.advance(w);
        assertEquals(40.0, hub.stockpile.get(Resource.ICE), 1e-9);
        assertEquals(ShipState.IDLE, s.state);
        assertNull(s.transit);
    }
}
