package spacecolony.ui.dialogs;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class DispatchShipDialogTest {
    @Test void colonizer_seesUnsettledBodies_haulerDoesNot() {
        World w = WorldGenerator.generate(1L);
        var forColonizer = DispatchShipDialog.destinations(w,
            new Ship("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
        assertEquals(new DispatchShipDialog.Destination(null, "mars"), forColonizer.get("Mars (unsettled)"));
        assertEquals(new DispatchShipDialog.Destination("site-earth-hub", null), forColonizer.get("site-earth-hub"));
        assertFalse(forColonizer.containsKey("Earth (unsettled)"));

        var forHauler = DispatchShipDialog.destinations(w,
            new Ship("h1", "H1", ShipClass.HAULER, "site-earth-hub"));
        assertEquals(1, forHauler.size());
        assertTrue(forHauler.containsKey("site-earth-hub"));
    }

    @Test void fuelShortfall_explainsAnUnaffordableTrip() {
        World w = WorldGenerator.generate(1L);
        Ship ark = new Ship("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub");
        var toMars = new DispatchShipDialog.Destination(null, "mars");
        assertNull(DispatchShipDialog.fuelShortfall(w, ark, toMars, Map.of()));
        w.findSite("site-earth-hub").stockpile.put(Resource.FUEL, 5.0);
        String msg = DispatchShipDialog.fuelShortfall(w, ark, toMars, Map.of());
        assertNotNull(msg);
        assertTrue(msg.startsWith("Not enough fuel at Earth Hub for this trip"), msg);
    }
}
