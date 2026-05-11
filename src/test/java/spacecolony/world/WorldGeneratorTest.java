package spacecolony.world;

import org.junit.jupiter.api.Test;
import spacecolony.sim.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldGeneratorTest {
    @Test
    void sameSeed_producesIdenticalWorlds() {
        World a = WorldGenerator.generate(123L);
        World b = WorldGenerator.generate(123L);
        assertEquals(a.bodies.size(), b.bodies.size());
        for (int i = 0; i < a.bodies.size(); i++) {
            assertEquals(a.bodies.get(i).id, b.bodies.get(i).id);
            assertEquals(a.bodies.get(i).surfaceSeed, b.bodies.get(i).surfaceSeed);
        }
        assertEquals(a.tick, b.tick);
        assertEquals(a.seed, b.seed);
    }

    @Test
    void differentSeeds_produceDifferentSurfaceSeeds() {
        World a = WorldGenerator.generate(1L);
        World b = WorldGenerator.generate(2L);
        boolean anyDifferent = false;
        for (int i = 0; i < a.bodies.size(); i++) {
            if (a.bodies.get(i).surfaceSeed != b.bodies.get(i).surfaceSeed) anyDifferent = true;
        }
        assertTrue(anyDifferent);
    }

    @Test
    void earthHasStartingSite() {
        World w = WorldGenerator.generate(42L);
        Body earth = w.findBody("earth");
        assertNotNull(earth);
        assertEquals(1, earth.sites.size());
        Site s = earth.sites.get(0);
        assertEquals(SystemLayout.STARTING_SITE_LAT, s.lat, 1e-9);
    }

    @Test
    void startingSiteHasInitialBuildingsAndStockpile() {
        World w = WorldGenerator.generate(7L);
        Site s = w.findBody("earth").sites.get(0);
        assertTrue(s.population > 0);
        assertTrue(s.buildings.size() >= 3, "starting site should have a few buildings");
        assertTrue(s.stockpile.get(Resource.FOOD) > 0);
    }

    @Test
    void allBodiesHaveResourceYieldMaps() {
        World w = WorldGenerator.generate(99L);
        for (Body b : w.bodies) {
            assertNotNull(b.resourceYields);
            assertTrue(b.resourceYields instanceof ResourceYieldMap);
        }
    }
}
