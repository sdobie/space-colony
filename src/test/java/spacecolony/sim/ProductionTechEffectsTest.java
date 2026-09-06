package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTechEffectsTest {

    @Test
    void basicMining_increasesOreOutputBy10pct() {
        double withoutTech = oreDeltaOneTick(false, false);
        double withTech    = oreDeltaOneTick(true, false);
        assertEquals(1.10, withTech / withoutTech, 1e-6);
    }

    @Test
    void autoMining_stacksWithBasicMining() {
        double base = oreDeltaOneTick(false, false);
        double both = oreDeltaOneTick(true, true);
        assertEquals(1.10 * 1.30, both / base, 1e-6);
    }

    @Test
    void hydroponics_increasesFoodAndDecreasesWaterDemand() {
        World wBase = WorldGenerator.generate(1L);
        World wTech = WorldGenerator.generate(1L);
        wTech.tech.researched.add("hydroponics");
        Simulator simBase = new Simulator(), simTech = new Simulator();

        Site sBase = wBase.findSite("site-earth-hub");
        Site sTech = wTech.findSite("site-earth-hub");
        double foodBase0 = sBase.stockpile.get(Resource.FOOD);
        double foodTech0 = sTech.stockpile.get(Resource.FOOD);
        double waterBase0 = sBase.stockpile.get(Resource.WATER);
        double waterTech0 = sTech.stockpile.get(Resource.WATER);

        simBase.advance(wBase);
        simTech.advance(wTech);

        double foodDeltaBase = sBase.stockpile.get(Resource.FOOD) - foodBase0;
        double foodDeltaTech = sTech.stockpile.get(Resource.FOOD) - foodTech0;
        double waterDeltaBase = waterBase0 - sBase.stockpile.get(Resource.WATER); // water consumed (positive)
        double waterDeltaTech = waterTech0 - sTech.stockpile.get(Resource.WATER);

        assertTrue(foodDeltaTech > foodDeltaBase * 1.25,
            "hydroponics should raise food output substantially (>=25% over baseline)");
        assertTrue(waterDeltaTech < waterDeltaBase,
            "hydroponics should reduce water consumption");
    }

    @Test
    void solarPanels_increasesPowerOutput() {
        // Make the site power-limited so multipliers are observable through the throttle.
        World wBase = WorldGenerator.generate(1L);
        World wTech = WorldGenerator.generate(1L);
        wTech.tech.researched.add("solar-panels");

        // Add lots of buildings so power demand exceeds supply at L1 plant.
        for (World w : new World[] { wBase, wTech }) {
            Site s = w.findSite("site-earth-hub");
            for (int i = 0; i < 10; i++) s.buildings.add(new Building(BuildingType.MINE, 1));
        }

        double oreBase0 = wBase.findSite("site-earth-hub").stockpile.get(Resource.ORE);
        double oreTech0 = wTech.findSite("site-earth-hub").stockpile.get(Resource.ORE);
        new Simulator().advance(wBase);
        new Simulator().advance(wTech);
        double dBase = wBase.findSite("site-earth-hub").stockpile.get(Resource.ORE) - oreBase0;
        double dTech = wTech.findSite("site-earth-hub").stockpile.get(Resource.ORE) - oreTech0;

        // More power -> higher powerFactor -> more ore from the same mines.
        assertTrue(dTech > dBase * 1.1, "solar-panels should noticeably raise power-throttled output");
    }

    @Test
    void smelting_increasesRefineryOutput() {
        World wBase = makeWorldWithRefinery(false);
        World wTech = makeWorldWithRefinery(true);
        double metalBase0 = wBase.findSite("site-earth-hub").stockpile.get(Resource.METAL);
        double metalTech0 = wTech.findSite("site-earth-hub").stockpile.get(Resource.METAL);
        new Simulator().advance(wBase);
        new Simulator().advance(wTech);
        double dBase = wBase.findSite("site-earth-hub").stockpile.get(Resource.METAL) - metalBase0;
        double dTech = wTech.findSite("site-earth-hub").stockpile.get(Resource.METAL) - metalTech0;
        assertEquals(1.20, dTech / dBase, 1e-3);
    }

    @Test
    void lifeSupportI_raisesMoraleCeilingTo120() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("life-support-i");
        Site s = w.findSite("site-earth-hub");
        s.morale = 1.0;
        // Advance several ticks under healthy supply — morale should drift up toward 1.20.
        Simulator sim = new Simulator();
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertTrue(s.morale > 1.0, "morale should exceed legacy 1.0 ceiling with life-support-i");
        assertTrue(s.morale <= 1.20 + 1e-6, "morale should not exceed life-support-i ceiling 1.20");
    }

    // --- helpers ---

    private static double oreDeltaOneTick(boolean basicMining, boolean autoMining) {
        World w = WorldGenerator.generate(1L);
        if (basicMining) w.tech.researched.add("basic-mining");
        if (autoMining)  w.tech.researched.add("auto-mining");
        Site s = w.findSite("site-earth-hub");
        double before = s.stockpile.get(Resource.ORE);
        new Simulator().advance(w);
        return s.stockpile.get(Resource.ORE) - before;
    }

    private static World makeWorldWithRefinery(boolean smelting) {
        World w = WorldGenerator.generate(1L);
        if (smelting) w.tech.researched.add("smelting");
        Site s = w.findSite("site-earth-hub");
        // Seed enough ORE so refinery is input-limited the same way in both worlds.
        s.stockpile.put(Resource.ORE, 500.0);
        s.buildings.add(new Building(BuildingType.REFINERY, 1));
        return w;
    }
}
