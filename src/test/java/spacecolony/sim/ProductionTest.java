package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTest {

    @Test
    void mineProducesOreEachTick() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        // Earth Hub has 1 MINE per WorldGenerator init.
        double before = s.stockpile.get(Resource.ORE);
        new Simulator().advance(w);
        assertTrue(s.stockpile.get(Resource.ORE) > before);
    }

    @Test
    void farmConsumesWaterAndProducesFood() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        double waterBefore = s.stockpile.get(Resource.WATER);
        double foodBefore = s.stockpile.get(Resource.FOOD);
        new Simulator().advance(w);
        assertTrue(s.stockpile.get(Resource.FOOD) > foodBefore);
        assertTrue(s.stockpile.get(Resource.WATER) < waterBefore);
    }

    @Test
    void noPower_throttlesProduction() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        // Disable the power plant; powerFactor=0, ORE production should be exactly 0.
        for (Building b : s.buildings) if (b.type == BuildingType.POWER_PLANT) b.enabled = false;
        double oreBefore = s.stockpile.get(Resource.ORE);
        new Simulator().advance(w);
        double delta = s.stockpile.get(Resource.ORE) - oreBefore;
        assertEquals(0.0, delta, 1e-9, "No power should produce no ore");
        // Regression guard: the same world WITH power produces some ore.
        World baseline = WorldGenerator.generate(1L);
        double baseBefore = baseline.findSite("site-earth-hub").stockpile.get(Resource.ORE);
        new Simulator().advance(baseline);
        double baseDelta = baseline.findSite("site-earth-hub").stockpile.get(Resource.ORE) - baseBefore;
        assertTrue(baseDelta > 0.0, "Baseline with power should produce ore");
    }

    @Test
    void productionCacheReflectsLastTickRate() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        new Simulator().advance(w);
        // FOOD net rate should be > 0 with farm operating.
        assertTrue(s.productionRateCache.get(Resource.FOOD) > 0);
    }
}
