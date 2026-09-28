package spacecolony.ui;

import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ColonyListPanelTest {
    private static World earth() {
        World w = WorldGenerator.generate(42L);
        w.randomEventsEnabled = false;
        return w;
    }

    @Test
    void healthyColonyIsPlain() {
        World w = earth();
        new Simulator().advance(w);
        assertEquals(UiColors.FOREGROUND, ColonyListPanel.rowColor(w.findSite("site-earth-hub")));
    }

    @Test
    void noFoodIsRed() {
        World w = earth();
        w.findSite("site-earth-hub").stockpile.put(Resource.FOOD, 0.0);
        assertEquals(UiColors.ERROR, ColonyListPanel.rowColor(w.findSite("site-earth-hub")));
    }

    @Test
    void waterRunningOutIsAmber() {
        World w = earth();
        Site hub = w.findSite("site-earth-hub");
        hub.stockpile.put(Resource.WATER, 4.0);   // -0.8 a day: 5 days
        new Simulator().advance(w);
        assertEquals(UiColors.WARNING, ColonyListPanel.rowColor(hub));
    }

    @Test
    void starvedBuildingIsAmber() {
        World w = earth();
        Site hub = w.findSite("site-earth-hub");
        hub.stockpile.put(Resource.BIOMASS, 0.0);
        new Simulator().advance(w);
        assertTrue(hub.buildings.stream().anyMatch(b -> b.type == BuildingType.FARM));
        assertEquals(UiColors.WARNING, ColonyListPanel.rowColor(hub));
    }
}
