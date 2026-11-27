package spacecolony.playtest;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** The play-tester's own move choice: it should take the way out the game offers. */
class ContinuousPlayTesterPlanTest {

    @Test
    void brokenRefineryWithNoMetalToRepairIt_buildsFurnace() {
        ContinuousPlayTester.style = ContinuousPlayTester.Style.BALANCED;
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        s.buildings.removeIf(b -> b.type == BuildingType.REFINERY || b.type == BuildingType.FURNACE);
        Building refinery = new Building(BuildingType.REFINERY, 1);
        s.buildings.add(refinery);
        s.stockpile.put(Resource.FOOD, 1000.0);
        s.stockpile.put(Resource.WATER, 1000.0);
        s.stockpile.put(Resource.COMPONENTS, 140.0);
        new Simulator().advance(w);
        refinery.enabled = false;               // broke down
        s.stockpile.put(Resource.METAL, 0.0);   // repair needs METAL, and only a refinery makes it

        ContinuousPlayTester.Plan p = ContinuousPlayTester.needs(w, s, true);

        assertNotNull(p, "the hub is stuck without METAL; the planner must act");
        assertTrue(p.desc().startsWith("build FURNACE"), "expected a furnace, got: " + p.desc());
    }
}
