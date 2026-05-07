package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipTest {
    @Test
    void newShip_isIdleAndEmpty() {
        Ship s = new Ship("ship-1", "Hauler Alpha", ShipClass.HAULER, "site-earth-hub");
        assertEquals(ShipState.IDLE, s.state);
        assertEquals("site-earth-hub", s.currentSiteId);
        assertNull(s.transit);
        assertEquals(0.0, s.cargo.get(Resource.METAL));
    }

    @Test
    void shipClass_carriesStats() {
        assertTrue(ShipClass.HAULER.cargoCap() > 0);
        assertTrue(ShipClass.HAULER.dryMass() > 0);
        assertTrue(ShipClass.HAULER.speed() > 0);
    }

    @Test
    void cargoMass_sumsCargo() {
        Ship s = new Ship("ship-2", "Tank", ShipClass.TANKER, "site-x");
        s.cargo.put(Resource.METAL, 100.0);
        s.cargo.put(Resource.WATER, 50.0);
        assertEquals(150.0, s.cargoMass(), 1e-9);
    }
}
