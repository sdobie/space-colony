package spacecolony.world;

import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import spacecolony.sim.Resource;
import static org.junit.jupiter.api.Assertions.*;

class ResourceYieldMapTest {
    @Test
    void sameSeedAndType_producesSameYields() {
        ResourceYieldMap a = new ResourceYieldMap(42L, BodyType.ROCKY);
        ResourceYieldMap b = new ResourceYieldMap(42L, BodyType.ROCKY);
        assertEquals(a.sample(Resource.ORE, 0.3, 0.5), b.sample(Resource.ORE, 0.3, 0.5), 1e-12);
        assertEquals(a.sample(Resource.ICE, -0.1, 0.0), b.sample(Resource.ICE, -0.1, 0.0), 1e-12);
    }

    @Test
    void rockyBody_hasNonzeroOreYield() {
        ResourceYieldMap m = new ResourceYieldMap(1L, BodyType.ROCKY);
        double max = 0.0;
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 50; j++) {
                double lat = -Math.PI / 2 + Math.PI * i / 49;
                double lon = -Math.PI + 2 * Math.PI * j / 49;
                max = Math.max(max, m.sample(Resource.ORE, lat, lon));
            }
        }
        assertTrue(max > 0.5, "Rocky body should have a high-ore region somewhere");
    }

    @Test
    void gasGiant_yieldsZeroOre() {
        ResourceYieldMap m = new ResourceYieldMap(1L, BodyType.GAS_GIANT);
        assertEquals(0.0, m.sample(Resource.ORE, 0.5, 0.5), 1e-12);
    }

    @Test
    void gasGiant_yieldsFuel() {
        ResourceYieldMap m = new ResourceYieldMap(1L, BodyType.GAS_GIANT);
        assertTrue(m.sample(Resource.FUEL, 0.0, 0.0) > 0.0);
    }

    @Test
    void asteroid_skewsToOreAndMetal() {
        ResourceYieldMap m = new ResourceYieldMap(1L, BodyType.ASTEROID);
        // Average ORE yield should be > average ICE yield on a generic asteroid.
        double oreSum = 0, iceSum = 0;
        int n = 0;
        for (int i = 0; i < 10; i++) for (int j = 0; j < 10; j++) {
            double lat = -1.0 + 2.0 * i / 9;
            double lon = -3.14 + 6.28 * j / 9;
            oreSum += m.sample(Resource.ORE, lat, lon);
            iceSum += m.sample(Resource.ICE, lat, lon);
            n++;
        }
        assertTrue(oreSum / n > iceSum / n, "Asteroid should yield more ore than ice on average");
    }
}
