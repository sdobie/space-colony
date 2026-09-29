package spacecolony.debug;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class FinishConstructionTest {
    @Test
    void everythingUnderWayFinishesNextTick() {
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite("site-earth-hub");
        Building shipyard = hub.addBuilding(new Building(BuildingType.SHIPYARD, 0));
        shipyard.daysLeft = 10;
        hub.buildings.get(1).daysLeft = 3;
        DebugActions.finishConstruction(hub);
        new Simulator().advance(w);
        assertTrue(hub.buildings.stream().noneMatch(Building::isUnderConstruction));
        assertEquals(1, shipyard.level);
        assertEquals(2, hub.buildings.get(1).level);
    }
}
