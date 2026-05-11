package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SupplyTest {
    @Test
    void starvedColony_loseMoraleAndPopulation() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        // Empty all stockpiles and remove farm so no food is generated.
        for (Resource r : Resource.values()) s.stockpile.put(r, 0.0);
        s.buildings.removeIf(b -> b.type == BuildingType.FARM);
        s.morale = 1.0;
        int popBefore = s.population;
        Simulator sim = new Simulator();
        for (int i = 0; i < 200; i++) sim.advance(w);
        assertTrue(s.morale < 0.4, "morale should drop under starvation");
        assertTrue(s.population < popBefore, "population should drop under starvation");
    }

    @Test
    void resuppliedColony_recovers() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        for (Resource r : Resource.values()) s.stockpile.put(r, 0.0);
        s.buildings.removeIf(b -> b.type == BuildingType.FARM);
        Simulator sim = new Simulator();
        for (int i = 0; i < 100; i++) sim.advance(w);
        double moraleLow = s.morale;
        // Resupply.
        s.stockpile.put(Resource.FOOD, 10000.0);
        s.stockpile.put(Resource.WATER, 10000.0);
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertTrue(s.morale > moraleLow, "morale should recover when resupplied");
    }
}
