package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class HabitatCapTest {

    @Test
    void earthHub_baseline_capIs300WithOneL1Habitat() {
        World w = WorldGenerator.generate(1L);
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // siteBase=200, one L1 HABITAT (+100) -> 300
        assertEquals(300, s.populationCap);
    }

    @Test
    void colonizerSite_baseline_capIs200WithOneL1Habitat() {
        World w = WorldGenerator.generate(1L);
        Site planted = new Site("site-mars-new", "Mars New", "mars", 0.0, 0.0, 100);
        planted.buildings.add(new Building(BuildingType.HABITAT, 1));
        w.findBody("mars").sites.add(planted);
        new Simulator().advance(w);
        assertEquals(200, planted.populationCap);
    }

    @Test
    void addingHabitat_raisesCapBy100PerLevel() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        s.buildings.add(new Building(BuildingType.HABITAT, 2));
        new Simulator().advance(w);
        // base 200 + L1 HABITAT (100) + new L2 HABITAT (200) = 500
        assertEquals(500, s.populationCap);
    }

    @Test
    void disabledHabitat_doesNotContribute() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        for (Building b : s.buildings) if (b.type == BuildingType.HABITAT) b.enabled = false;
        new Simulator().advance(w);
        assertEquals(200, s.populationCap);
    }

    @Test
    void colonyMgmtI_appliesMultiplier() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("colony-mgmt-i");
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // (200 + 100) * 1.20 = 360
        assertEquals(360, s.populationCap);
    }

    @Test
    void bothMgmtTechs_stackMultiplicatively() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("colony-mgmt-i");
        w.tech.researched.add("colony-mgmt-ii");
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // (200 + 100) * 1.20 * 1.30 = 468
        assertEquals(468, s.populationCap);
    }
}
