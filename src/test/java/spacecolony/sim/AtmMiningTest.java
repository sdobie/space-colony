package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class AtmMiningTest {

    @Test
    void withoutAtmMining_gasGiantMine_producesNoFuel() {
        World w = WorldGenerator.generate(1L);
        Site jovianMine = plantGasGiantMine(w);
        double fuelBefore = jovianMine.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertEquals(fuelBefore, jovianMine.stockpile.get(Resource.FUEL), 1e-9,
            "gas-giant MINE should produce 0 FUEL without atm-mining");
    }

    @Test
    void withAtmMining_gasGiantMine_producesFuel() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("atm-mining");
        Site jovianMine = plantGasGiantMine(w);
        double fuelBefore = jovianMine.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertTrue(jovianMine.stockpile.get(Resource.FUEL) > fuelBefore,
            "gas-giant MINE should produce FUEL with atm-mining researched");
    }

    @Test
    void withAtmMining_rockyBodyMine_doesNotChangeFuelBehavior() {
        // Rocky-body MINE should never produce FUEL regardless of tech (sampler returns 0).
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("atm-mining");
        Site earthHub = w.findSite("site-earth-hub");
        double fuelBefore = earthHub.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertEquals(fuelBefore, earthHub.stockpile.get(Resource.FUEL), 1e-9);
    }

    private static Site plantGasGiantMine(World w) {
        Site jovianSite = new Site("site-jovian-1", "Jovian Cloud", "jovian", 0.0, 0.0, 100);
        jovianSite.buildings.add(new Building(BuildingType.MINE, 1));
        // Power plant so the mine isn't fully throttled.
        jovianSite.buildings.add(new Building(BuildingType.POWER_PLANT, 1));
        w.findBody("jovian").sites.add(jovianSite);
        return jovianSite;
    }
}
