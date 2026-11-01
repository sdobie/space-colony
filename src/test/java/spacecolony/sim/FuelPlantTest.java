package spacecolony.sim;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.economy.BuildingOutcome;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.Limit;
import spacecolony.sim.economy.SiteEconomy;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 10 §4.3: the fuel plant brews SYNFUEL from BIOMASS, after the farms. */
class FuelPlantTest {
    private static Site site(World w, List<Building> buildings, double biomass, double water) {
        Site s = new Site("site-x", "X", "mars", 0, 0, 100);
        for (Building b : buildings) s.addBuilding(b);
        s.stockpile.put(Resource.BIOMASS, biomass);
        s.stockpile.put(Resource.WATER, water);
        s.stockpile.put(Resource.FOOD, 100.0);
        w.findBody("mars").sites.add(s);
        return s;
    }

    private static DayReport run(World w, Site s, Map<Resource, Double> stock) {
        return SiteEconomy.run(w, w.findBody("mars"), s, s.buildings, stock);
    }

    @Test void oneBiomassIn_oneSynfuelOut_perLevel() {
        World w = WorldGenerator.generate(1L);
        Site s = site(w, List.of(new Building(BuildingType.POWER_PLANT, 3), new Building(BuildingType.FUEL_PLANT, 2)), 50, 0);
        Map<Resource, Double> stock = new EnumMap<>(s.stockpile);
        run(w, s, stock);
        assertEquals(48.0, stock.get(Resource.BIOMASS), 1e-9);
        assertEquals(2.0, stock.get(Resource.SYNFUEL), 1e-9);
    }

    @Test void farmsTakeTheirBiomassFirst() {
        World w = WorldGenerator.generate(1L);
        // The fuel plant is listed first, but still waits for the farm.
        Site s = site(w, List.of(new Building(BuildingType.POWER_PLANT, 3), new Building(BuildingType.FUEL_PLANT, 1),
                                 new Building(BuildingType.FARM, 1)), 0.7, 10);
        Map<Resource, Double> stock = new EnumMap<>(s.stockpile);
        DayReport d = run(w, s, stock);
        BuildingOutcome farm = d.outcome(2);
        assertNull(farm.limit(), "the farm got all it asked for");
        BuildingOutcome plant = d.outcome(1);
        assertEquals(Limit.SHORT_INPUT, plant.limit());
        assertEquals(Resource.BIOMASS, plant.limitResource());
        assertEquals(0.2, stock.get(Resource.SYNFUEL), 1e-9);
    }

    @Test void noBiomass_isIdle() {
        World w = WorldGenerator.generate(1L);
        Site s = site(w, List.of(new Building(BuildingType.POWER_PLANT, 3), new Building(BuildingType.FUEL_PLANT, 1)), 0, 0);
        DayReport d = run(w, s, new EnumMap<>(s.stockpile));
        assertEquals(Limit.NO_INPUT, d.outcome(1).limit());
    }

    @Test void brownout_slowsIt() {
        World w = WorldGenerator.generate(1L);
        Site s = site(w, List.of(new Building(BuildingType.FUEL_PLANT, 1)), 50, 0);
        Map<Resource, Double> stock = new EnumMap<>(s.stockpile);
        run(w, s, stock);
        assertEquals(0.0, stock.getOrDefault(Resource.SYNFUEL, 0.0), 1e-9);
    }

    @Test void catalog() {
        BuildingSpec spec = BuildingCatalog.get(BuildingType.FUEL_PLANT);
        assertEquals("Fuel plant", spec.displayName());
        assertEquals(BuildCost.of(20, 10, 4), spec.cost());
    }
}
