package spacecolony.ui;

import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
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

    @Test
    void shipStatusSaysWhereTheShipIsOrIsHeaded() {
        World w = earth();
        Ship s = new Ship("x1", "Mule", ShipClass.HAULER, "site-earth-hub");
        assertEquals("Docked at Earth Hub", ColonyListPanel.shipStatusText(w, s));

        s.state = ShipState.LOADING;
        s.transit = new Transit("site-earth-hub", null, "mars", w.tick, Transit.PENDING_ARRIVAL_TICK, Transit.snapshot(s.cargo));
        assertEquals("Loading for Mars", ColonyListPanel.shipStatusText(w, s));

        s.state = ShipState.IN_TRANSIT;
        s.currentSiteId = null;
        s.transit = new Transit("site-earth-hub", null, "mars", w.tick, w.tick + 12, Transit.snapshot(s.cargo));
        assertEquals("→ Mars · 12d", ColonyListPanel.shipStatusText(w, s));

        s.state = ShipState.IDLE;
        s.transit = null;
        s.orbitingBodyId = "mars";
        assertEquals("Orbiting Mars", ColonyListPanel.shipStatusText(w, s));
    }

    @Test
    void empireTotalsSumEveryColony() {
        World w = earth();
        new Simulator().advance(w);
        Site hub = w.findSite("site-earth-hub");
        var t = ColonyListPanel.EmpireTotals.of(w);
        assertEquals(hub.population, t.population());
        assertEquals(hub.stockpile.get(Resource.FOOD), t.stock().get(Resource.FOOD), 1e-9);
        assertEquals(hub.lastDay.net(Resource.WATER), t.net().get(Resource.WATER), 1e-9);
    }

    @Test
    void compactFiguresFitATile() {
        assertEquals("212", ColonyListPanel.compact(212.4));
        assertEquals("1.2k", ColonyListPanel.compact(1234));
        assertEquals("12k", ColonyListPanel.compact(12_345));
    }
}
