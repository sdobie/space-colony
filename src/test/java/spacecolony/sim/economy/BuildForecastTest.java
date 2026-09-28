package spacecolony.sim.economy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.save.SaveFile;
import spacecolony.sim.BodyType;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.sim.BuildingType.*;

class BuildForecastTest {
    private static final String HUB = "site-earth-hub";

    private static World earth() {
        World w = WorldGenerator.generate(42L);
        w.randomEventsEnabled = false;
        return w;
    }

    private static boolean has(List<String> lines, String... parts) {
        return lines.stream().anyMatch(l -> {
            for (String p : parts) if (!l.contains(p)) return false;
            return true;
        });
    }

    @Test
    void forecastingLeavesTheWorldAlone() {
        World w = earth();
        String before = SaveFile.toJson(w);
        for (BuildingType t : BuildingType.values()) BuildForecast.of(w, w.findSite(HUB), t);
        assertEquals(before, SaveFile.toJson(w));
    }

    @Test
    void farmWarnsAboutBiomass() {
        World w = earth();
        BuildForecast f = BuildForecast.of(w, w.findSite(HUB), FARM);
        assertEquals(1.5, f.delta(Resource.FOOD), 1e-9);
        assertEquals(-0.5, f.delta(Resource.BIOMASS), 1e-9);
        assertTrue(has(f.warnings(), "Needs BIOMASS", "Nothing here makes it", "about 100 days"), f.warnings().toString());
    }

    @Test
    void secondLabBrownsOutEarthHub() {
        World w = earth();
        BuildForecast first = BuildForecast.of(w, w.findSite(HUB), RESEARCH_LAB);
        assertFalse(has(first.warnings(), "Power"), first.warnings().toString());
        assertTrue(has(first.facts(), "Research 0.0 → 1.0"), first.facts().toString());
        w.findSite(HUB).buildings.add(new spacecolony.sim.Building(RESEARCH_LAB, 1));
        BuildForecast second = BuildForecast.of(w, w.findSite(HUB), RESEARCH_LAB);
        assertTrue(has(second.warnings(), "Power short: 10.0 made, 12.0 needed", "83%"), second.warnings().toString());
    }

    @Test
    void powerPlantEndsABrownout() {
        World w = earth();
        Site hub = w.findSite(HUB);
        for (int i = 0; i < 3; i++) hub.buildings.add(new spacecolony.sim.Building(RESEARCH_LAB, 1));
        BuildForecast f = BuildForecast.of(w, hub, POWER_PLANT);
        assertTrue(f.before().powerFactor < 1.0);
        assertEquals(1.0, f.after().powerFactor, 1e-9);
        assertTrue(f.warnings().isEmpty(), f.warnings().toString());
        assertTrue(has(f.facts(), "Power 10.0 → 20.0 made"), f.facts().toString());
    }

    @Test
    void habitatRaisesTheCap() {
        World w = earth();
        BuildForecast f = BuildForecast.of(w, w.findSite(HUB), HABITAT);
        assertTrue(has(f.facts(), "Population cap 300 → 400"), f.facts().toString());
    }

    @Test
    void mineOnBarrenGround() {
        TestWorlds t = TestWorlds.at1Au(Map.of()).with(POWER_PLANT);
        BuildForecast f = BuildForecast.of(t.world, t.site, MINE);
        assertTrue(has(f.warnings(), "No ORE in the ground here"), f.warnings().toString());
    }

    @Test
    void gasGiantMineAsksForTheTech() {
        TestWorlds t = new TestWorlds(5.0, BodyType.GAS_GIANT, Map.of(Resource.FUEL, 0.8)).with(POWER_PLANT);
        t.site.buildings.get(0).level = 30;
        BuildForecast f = BuildForecast.of(t.world, t.site, MINE);
        assertTrue(has(f.warnings(), "Atmospheric Mining"), f.warnings().toString());
    }

    @Test
    void refineryWithoutIceSaysSo() {
        World w = earth();
        BuildForecast f = BuildForecast.of(w, w.findSite(HUB), REFINERY);
        assertTrue(has(f.warnings(), "Needs ICE", "none here"), f.warnings().toString());
    }

    @Test
    void theForecastComesTrue() {
        for (BuildingType type : BuildingType.values()) {
            World w = earth();
            Site hub = w.findSite(HUB);
            BuildForecast f = BuildForecast.of(w, hub, type);
            Simulator sim = new Simulator();
            sim.enqueue(new BuildBuildingCommand(HUB, type));
            sim.advance(w);
            for (Resource r : Resource.values()) {
                if (!r.isStockpileable()) continue;
                // Tick 1 moves the planets a little, so power (and anything it throttles) can
                // drift by a hair from the tick-0 forecast.
                assertEquals(f.after().net(r), hub.lastDay.net(r), 1e-3, type + " " + r);
            }
        }
    }

    @Test
    void estimateIsMarked() {
        World w = earth();
        DayReport d = BuildForecast.estimate(w, w.findSite(HUB));
        assertTrue(d.estimate);
        assertEquals(10.0, d.powerMade, 1e-6);
    }
}
