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

    @Test void overCapacity_explainsTheHoldLimit() {
        Ship ark = new Ship("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub");
        assertNull(spacecolony.sim.phases.CommandPhase.cargoOverflow(ark, Map.of(Resource.FOOD, 100.0)));
        String msg = spacecolony.sim.phases.CommandPhase.cargoOverflow(ark,
            Map.of(Resource.FOOD, 60.0, Resource.WATER, 50.0));
        assertEquals("Manifest of 110 is more than Ark can carry (100)", msg);
    }

    @Test void explorer_seesEveryBodyWithItsSurveyTag() {
        World w = WorldGenerator.generate(1L);
        Ship x = new Ship("x1", "Scout-1", ShipClass.EXPLORER, "site-earth-hub");
        var d = DispatchShipDialog.destinations(w, x);
        assertTrue(d.containsKey("site-earth-hub"));
        assertEquals(new DispatchShipDialog.Destination(null, "earth"), d.get("Earth (surveyed)"));
        assertEquals(new DispatchShipDialog.Destination(null, "mars"), d.get("Mars (unsurveyed)"));
        assertEquals(1 + w.bodies.size(), d.size());

        x.currentSiteId = null;
        x.orbitingBodyId = "mars";
        var fromMars = DispatchShipDialog.destinations(w, x);
        assertFalse(fromMars.containsKey("Mars (unsurveyed)"), "not the body it orbits");
        assertEquals(w.bodies.size(), fromMars.size());
    }

    @Test void explorer_inOrbit_tankShortfallExplained() {
        World w = WorldGenerator.generate(1L);
        Ship x = new Ship("x1", "Scout-1", ShipClass.EXPLORER, null);
        x.orbitingBodyId = "mars";
        x.fuel = 0.5;
        String msg = DispatchShipDialog.fuelShortfall(w, x, new DispatchShipDialog.Destination(null, "jovian"), Map.of());
        assertNotNull(msg);
        assertTrue(msg.startsWith("Not enough fuel in Scout-1's tank"), msg);
        x.fuel = 100;
        assertNull(DispatchShipDialog.fuelShortfall(w, x, new DispatchShipDialog.Destination(null, "belt-a"), Map.of()));
    }
}
