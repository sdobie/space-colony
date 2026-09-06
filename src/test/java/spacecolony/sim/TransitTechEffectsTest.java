package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class TransitTechEffectsTest {

    @Test
    void ionDrives_cutsDepartureFuelCostBy20pct() {
        double baseCost = fuelConsumedOnDispatch(false);
        double techCost = fuelConsumedOnDispatch(true);
        assertEquals(0.80, techCost / baseCost, 1e-6);
    }

    @Test
    void fusionPlusIon_stacksTo56pct() {
        double base = fuelConsumedOnDispatch("ion-drives", "fusion-drives");
        double pure = fuelConsumedOnDispatch();
        assertEquals(0.80 * 0.70, base / pure, 1e-6);
    }

    @Test
    void allDriveTechs_reduceCostBy72pct() {
        double full = fuelConsumedOnDispatch("ion-drives", "fusion-drives", "antimatter");
        double pure = fuelConsumedOnDispatch();
        assertEquals(0.80 * 0.70 * 0.50, full / pure, 1e-6);
    }

    /** Drive the ship through LOADING -> IN_TRANSIT and return fuel consumed at departure. */
    private static double fuelConsumedOnDispatch(String... techs) {
        World w = WorldGenerator.generate(1L);
        for (String id : techs) w.tech.researched.add(id);

        Site origin = w.findSite("site-earth-hub");
        origin.stockpile.put(Resource.METAL, 200.0);

        // Stub a destination site at Mars so transit math has a target.
        Site mars = new Site("site-mars-stub", "Mars Stub", "mars", 0.0, 0.0, 100);
        w.findBody("mars").sites.add(mars);

        Ship s = new Ship("ship-h1", "H1", ShipClass.HAULER, "site-earth-hub");
        s.fuel = 1_000_000.0;
        w.ships.add(s);

        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", "site-mars-stub", Map.of(Resource.METAL, 50.0)));
        double fuelBefore = s.fuel;
        for (int i = 0; i < 200 && s.state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, s.state);
        return fuelBefore - s.fuel;
    }

    private static double fuelConsumedOnDispatch(boolean ionDrives) {
        return ionDrives ? fuelConsumedOnDispatch("ion-drives") : fuelConsumedOnDispatch();
    }
}
