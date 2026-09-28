package spacecolony.sim.economy;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.sim.BuildingType.*;

class SiteEconomyTest {
    private static final double EPS = 1e-9;

    @Test
    void farmWithoutBiomassIsIdle() {
        TestWorlds t = TestWorlds.at1Au(Map.of()).with(POWER_PLANT, FARM).stock(Resource.WATER, 50);
        DayReport d = t.runOnCopy();
        FlowLine food = d.linesOf(1).stream().filter(l -> l.resource() == Resource.FOOD).findFirst().orElseThrow();
        assertEquals(0.0, food.amount(), EPS);
        assertEquals(1.5, food.wanted(), EPS);
        FlowLine bio = d.linesOf(1).stream().filter(l -> l.resource() == Resource.BIOMASS).findFirst().orElseThrow();
        assertEquals(0.0, bio.amount(), EPS);
        assertEquals(-0.5, bio.wanted(), EPS);
        BuildingOutcome o = d.outcome(1);
        assertEquals(Limit.NO_INPUT, o.limit());
        assertEquals(Resource.BIOMASS, o.limitResource());
        assertEquals(0.0, o.efficiency(), EPS);
        assertTrue(o.starved());
    }

    @Test
    void secondFarmGetsWhatIsLeft() {
        TestWorlds t = TestWorlds.at1Au(Map.of()).with(POWER_PLANT, FARM, FARM)
            .stock(Resource.BIOMASS, 0.7).stock(Resource.WATER, 50);
        DayReport d = t.runOnCopy();
        assertNull(d.outcome(1).limit());
        assertEquals(1.0, d.outcome(1).efficiency(), EPS);
        assertEquals(Limit.SHORT_INPUT, d.outcome(2).limit());
        assertEquals(0.4, d.outcome(2).efficiency(), 1e-6);
    }

    @Test
    void brownoutSlowsProducersButNotLabs() {
        // One plant at 2 AU makes 2.5; four consumers want 8.
        TestWorlds t = new TestWorlds(2.0, BodyType.ROCKY, Map.of(Resource.ORE, 1.0, Resource.SILICATE, 1.0))
            .with(POWER_PLANT, MINE, FARM, RESEARCH_LAB, HABITAT).stock(Resource.BIOMASS, 10).stock(Resource.WATER, 10);
        DayReport d = t.runOnCopy();
        assertEquals(2.5, d.powerMade, 1e-6);
        assertEquals(8.0, d.powerUsed, EPS);
        assertEquals(2.5 / 8.0, d.powerFactor, 1e-6);
        assertEquals(Limit.BROWNOUT, d.outcome(1).limit());
        assertEquals(Limit.BROWNOUT, d.outcome(2).limit());
        assertEquals(2.5 / 8.0, d.outcome(1).efficiency(), 1e-6);
        assertNull(d.outcome(3).limit());
        assertNull(d.outcome(4).limit());
    }

    @Test
    void mineOnBarrenGround() {
        DayReport none = TestWorlds.at1Au(Map.of()).with(POWER_PLANT, MINE).runOnCopy();
        assertEquals(Limit.NO_YIELD, none.outcome(1).limit());
        assertEquals(Resource.ORE, none.outcome(1).limitResource());
        DayReport poor = TestWorlds.at1Au(Map.of(Resource.ORE, 0.2)).with(POWER_PLANT, MINE).runOnCopy();
        assertEquals(Limit.LOW_YIELD, poor.outcome(1).limit());
        assertEquals(1.0, poor.outcome(1).efficiency(), EPS);
        assertEquals(0.4, poor.net(Resource.ORE), 1e-9);
    }

    @Test
    void mineDigsIceWhereTheGroundHoldsIt() {
        DayReport d = new TestWorlds(1.0, BodyType.ICE_BODY, Map.of(Resource.ICE, 0.7, Resource.ORE, 0.1))
            .with(POWER_PLANT, MINE).runOnCopy();
        assertEquals(1.4, d.net(Resource.ICE), 1e-9);
        assertEquals(0.2, d.net(Resource.ORE), 1e-9);
        assertNull(d.outcome(1).limit(), "judged by its ice, not its poor ore");
    }

    @Test
    void mineIceFeedsTheRefinery() {
        DayReport d = TestWorlds.at1Au(Map.of(Resource.ICE, 0.5)).with(POWER_PLANT, POWER_PLANT, MINE, REFINERY).runOnCopy();
        // The mine's 1.0 ICE lands before the refinery runs, which turns it into 0.9 WATER.
        assertEquals(0.9, d.net(Resource.WATER), 1e-9);
        assertEquals(0.0, d.net(Resource.ICE), 1e-9);
    }

    @Test
    void gasGiantMineNeedsTheTech() {
        TestWorlds t = new TestWorlds(5.0, BodyType.GAS_GIANT, Map.of(Resource.FUEL, 0.8))
            .with(POWER_PLANT, POWER_PLANT, MINE);
        t.site.buildings.get(0).level = 30;  // enough sun at 5 AU
        DayReport before = t.runOnCopy();
        assertEquals(Limit.NEEDS_TECH, before.outcome(2).limit());
        assertEquals(Resource.FUEL, before.outcome(2).limitResource());
        t.world.tech.researched.add("atm-mining");
        DayReport after = t.runOnCopy();
        assertNull(after.outcome(2).limit());
        assertEquals(1.6, after.net(Resource.FUEL), 1e-9);
    }

    @Test
    void disabledBuildingDoesNothing() {
        TestWorlds t = TestWorlds.at1Au(Map.of(Resource.ORE, 1.0)).with(POWER_PLANT, MINE);
        t.site.buildings.get(1).enabled = false;
        DayReport d = t.runOnCopy();
        assertTrue(d.linesOf(1).isEmpty());
        assertEquals(Limit.DISABLED, d.outcome(1).limit());
        assertFalse(d.outcome(1).enabled());
    }

    @Test
    void refineryNamesTheMissingInput() {
        DayReport d = TestWorlds.at1Au(Map.of()).with(POWER_PLANT, REFINERY).stock(Resource.ORE, 100).runOnCopy();
        assertEquals(Limit.SHORT_INPUT, d.outcome(1).limit());
        assertEquals(Resource.ICE, d.outcome(1).limitResource());
        DayReport idle = TestWorlds.at1Au(Map.of()).with(POWER_PLANT, REFINERY).runOnCopy();
        assertEquals(Limit.NO_INPUT, idle.outcome(1).limit());
    }

    @Test
    void touchesOnlyTheStockItIsGiven() {
        TestWorlds t = TestWorlds.at1Au(Map.of(Resource.ORE, 1.0, Resource.SILICATE, 0.5))
            .with(POWER_PLANT, MINE, FARM, REFINERY).stock(Resource.BIOMASS, 20).stock(Resource.WATER, 20).stock(Resource.ICE, 5);
        t.site.population = 100;
        Map<Resource, Double> before = new EnumMap<>(t.site.stockpile);
        Map<Resource, Double> copy = new EnumMap<>(t.site.stockpile);
        DayReport d = SiteEconomy.run(t.world, t.body, t.site, t.site.buildings, copy);
        assertEquals(before, t.site.stockpile);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(copy.get(r) - before.get(r), d.net(r), 1e-9, r.name());
        }
        assertTrue(d.linesFor(Resource.FOOD).get(0).amount() > 0, "producers first");
    }

    @Test
    void outcomesCoverEveryBuilding() {
        TestWorlds t = TestWorlds.at1Au(Map.of()).with(BuildingType.values());
        assertEquals(BuildingType.values().length, t.runOnCopy().buildings().size());
    }
}
