package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.phases.TransitPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class TransitFuelCostTest {

    /** Fuel actually deducted at departure for a small METAL run Earth → Mars. */
    private static double[] dispatchAndMeasure(boolean ionDrives) {
        World w = WorldGenerator.generate(1L);
        if (ionDrives) w.tech.researched.add("ion-drives");
        Ship s = new Ship("ship-h1", "H1", ShipClass.HAULER, "site-earth-hub");
        s.fuel = 100.0;
        w.ships.add(s);
        w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
        Site mars = new Site("site-mars-test", "Mars Test", "mars", 0.0, 0.0, 50);
        w.findBody("mars").sites.add(mars);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand(s.id, mars.id, Map.of(Resource.METAL, 5.0)));
        double fuelBefore = s.fuel;
        for (int i = 0; i < 50 && s.state != ShipState.IN_TRANSIT; i++) {
            fuelBefore = s.fuel;
            sim.advance(w);
        }
        assertEquals(ShipState.IN_TRANSIT, s.state);
        double expected = TransitPhase.fuelCost(w, s.shipClass,
            s.transit.cargoSnapshot().values().stream().mapToDouble(Double::doubleValue).sum(),
            s.transit.originSiteId(), s.transit.destSiteId(),
            s.transit.departureTick(), s.transit.arrivalTick());
        return new double[] { fuelBefore - s.fuel, expected };
    }

    @Test
    void fuelCost_matchesWhatDepartureDeducts() {
        double[] r = dispatchAndMeasure(false);
        assertTrue(r[0] > 0);
        assertEquals(r[0], r[1], 1e-9);
    }

    @Test
    void ionDrives_cutDeductedFuelTo80Percent() {
        double plain = dispatchAndMeasure(false)[0];
        double[] ion = dispatchAndMeasure(true);
        assertEquals(ion[0], ion[1], 1e-9);
        assertEquals(0.8 * plain, ion[0], 1e-9);
    }
}
