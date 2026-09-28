package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingSpec.Rate;
import spacecolony.sim.BuildingSpec.Rate.Kind;
import static org.junit.jupiter.api.Assertions.*;

class BuildingCatalogTest {
    @Test
    void everyTypeHasASpec() {
        assertEquals(BuildingType.values().length, BuildingCatalog.all().size());
        for (BuildingType t : BuildingType.values()) {
            BuildingSpec s = BuildingCatalog.get(t);
            assertNotNull(s, t.name());
            assertEquals(t, s.type());
            assertFalse(s.displayName().isBlank());
            assertFalse(s.summary().isBlank());
        }
    }

    @Test
    void onlyPowerPlantsDrawNoPower() {
        for (BuildingSpec s : BuildingCatalog.all())
            assertEquals(s.type() == BuildingType.POWER_PLANT ? 0.0 : 2.0, s.powerDrawPerLevel(), s.type().name());
    }

    @Test
    void farmAndMineRates() {
        var farm = BuildingCatalog.get(BuildingType.FARM).rates();
        assertTrue(farm.contains(new Rate(Resource.BIOMASS, 0.5, Kind.INPUT)));
        assertTrue(farm.contains(new Rate(Resource.WATER, 0.3, Kind.INPUT)));
        assertTrue(farm.contains(new Rate(Resource.FOOD, 1.5, Kind.OUTPUT)));
        assertTrue(farm.contains(new Rate(Resource.BIOMASS, 0.3, Kind.OUTPUT)));
        assertTrue(farm.contains(new Rate(Resource.BIOMASS, 2.5, Kind.YIELD_OUTPUT)));
        var mine = BuildingCatalog.get(BuildingType.MINE).rates();
        assertTrue(mine.contains(new Rate(Resource.ORE, 2.0, Kind.YIELD_OUTPUT)));
        assertTrue(mine.contains(new Rate(Resource.SILICATE, 1.0, Kind.YIELD_OUTPUT)));
    }
}
