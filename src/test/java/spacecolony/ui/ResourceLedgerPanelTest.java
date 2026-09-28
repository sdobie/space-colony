package spacecolony.ui;

import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.economy.BuildForecast;
import spacecolony.sim.economy.Outlook;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ResourceLedgerPanelTest {
    private static World tickedEarth(int ticks) {
        World w = WorldGenerator.generate(42L);
        w.randomEventsEnabled = false;
        Simulator sim = new Simulator();
        for (int i = 0; i < ticks; i++) sim.advance(w);
        return w;
    }

    @Test
    void rowsForStockOrFlow_andFoodExpandsToItsSources() throws Exception {
        Edt.run(() -> {
            World w = tickedEarth(1);
            Site hub = w.findSite("site-earth-hub");
            ResourceLedgerPanel p = new ResourceLedgerPanel();
            p.update(hub, hub.lastDay);
            String text = p.text();
            assertTrue(text.contains("▸ FOOD"), text);
            assertTrue(text.contains("▸ COMPONENTS"), "stock without flow still shows");
            assertFalse(text.contains("ICE"), "no stock, no flow: hidden");
            assertFalse(text.contains("(estimate)"));

            JLabel food = p.rowLabel(ResourceLedgerPanel.ROW_PREFIX + "FOOD");
            food.getMouseListeners()[0].mousePressed(null);
            text = p.text();
            assertTrue(text.contains("▾ FOOD"), text);
            assertTrue(text.contains("Farm L1"), text);
            assertTrue(text.contains("Population (100)"), text);

            p.update(hub, hub.lastDay);
            assertTrue(p.isExpanded(Resource.FOOD), "stays open across refreshes");
            p.collapseAll();
            p.update(hub, hub.lastDay);
            assertFalse(p.text().contains("Farm L1"));
        });
    }

    @Test
    void brownoutShowsOnThePowerRow() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(42L);
            Site hub = w.findSite("site-earth-hub");
            hub.buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
            hub.buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
            ResourceLedgerPanel p = new ResourceLedgerPanel();
            p.update(hub, BuildForecast.estimate(w, hub));
            String text = p.text();
            assertTrue(text.contains("Resources (estimate)"), text);
            assertTrue(text.contains("brownout 83%"), text);
            p.togglePower();
            assertTrue(p.text().contains("Power plant L1"), p.text());
        });
    }

    @Test
    void lineAndOutlookText() {
        World w = WorldGenerator.generate(42L);
        Site hub = w.findSite("site-earth-hub");
        hub.stockpile.put(Resource.BIOMASS, 0.0);
        var d = BuildForecast.estimate(w, hub);
        var farmFood = d.linesFor(Resource.FOOD).stream()
            .filter(l -> ResourceLedgerPanel.sourceName(l.source()).equals("Farm L1")).findFirst().orElseThrow();
        assertEquals("+0.0  Farm L1 (wants 1.5; no BIOMASS)", ResourceLedgerPanel.lineText(farmFood, d));
        assertEquals("empty 12 d", ResourceLedgerPanel.outlookText(new Outlook.EmptyIn(12)));
        assertEquals("full 999+ d", ResourceLedgerPanel.outlookText(new Outlook.FullIn(999)));
        assertEquals("steady", ResourceLedgerPanel.outlookText(Outlook.of(5, 1000, 0)));
    }
}
