package spacecolony.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.testutil.Edt;
import spacecolony.ui.dialogs.BuildShipDialog;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 10 §6: ship builds and research progress in the right column, the left column and on the map. */
class ProgressVisualsTest {
    private static final String HUB = "site-earth-hub";

    /** Earth Hub with a lab, Ion Drives 120 / 300 under research, and a hauler on the slipway. */
    private static Engine busyEngine() {
        World w = WorldGenerator.generate(1L);
        w.randomEventsEnabled = false;
        Site hub = w.findSite(HUB);
        hub.addBuilding(new Building(BuildingType.RESEARCH_LAB, 1));
        w.tech.activeId = "ion-drives";
        w.tech.accumulatedPoints = 120;
        Engine e = new Engine(w);
        e.enqueue(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        e.tick();
        w.tech.accumulatedPoints = 120; // the lab added a point
        return e;
    }

    private static List<ProgressBar> bars(Component c) {
        List<ProgressBar> out = new ArrayList<>();
        if (c instanceof ProgressBar b) out.add(b);
        if (c instanceof Container k) for (Component child : k.getComponents()) out.addAll(bars(child));
        return out;
    }

    private static ProgressBar bar(Component root, String name) {
        return bars(root).stream().filter(b -> name.equals(b.getName())).findFirst().orElse(null);
    }

    private static void paint(Component p, int w, int h) {
        p.setSize(w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try { p.paint(g); } finally { g.dispose(); }
    }

    @Test void colonyRows_showResearchAndShipProgress() throws Exception {
        Edt.run(() -> {
            Engine e = busyEngine();
            World w = e.world();
            Site hub = w.findSite(HUB);
            DetailPanel p = new DetailPanel(e);
            e.setSelection(Selection.site(HUB));
            paint(p, 330, 1200);
            String text = PanelSmokeTest.allText(p);
            assertTrue(text.contains("Research lab L1   +1.0 research · Ion Drives 120 / 300"), text);
            assertTrue(text.contains("Shipyard L1   building Mule, 19 days left"), text);
            Building lab = hub.buildings.stream().filter(b -> b.type == BuildingType.RESEARCH_LAB).findFirst().orElseThrow();
            Building yard = hub.buildings.stream().filter(b -> b.type == BuildingType.SHIPYARD).findFirst().orElseThrow();
            assertEquals(0.4, bar(p, DetailPanel.TARGET_PROGRESS_PREFIX + lab.id).fraction(), 1e-9);
            assertEquals(1.0 / 20, bar(p, DetailPanel.TARGET_PROGRESS_PREFIX + yard.id).fraction(), 1e-9);
            // A farm has no job to show.
            Building farm = hub.buildings.stream().filter(b -> b.type == BuildingType.FARM).findFirst().orElseThrow();
            assertNull(bar(p, DetailPanel.TARGET_PROGRESS_PREFIX + farm.id));
        });
    }

    @Test void idleShipyard_andNoResearch_haveNoBar() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(1L);
            Site hub = w.findSite(HUB);
            hub.addBuilding(new Building(BuildingType.RESEARCH_LAB, 1));
            Engine e = new Engine(w);
            DetailPanel p = new DetailPanel(e);
            e.setSelection(Selection.site(HUB));
            paint(p, 330, 1200);
            String text = PanelSmokeTest.allText(p);
            assertTrue(text.contains("Shipyard L1   idle"), text);
            assertTrue(text.contains("no research queued"), text);
            assertTrue(bars(p).isEmpty());
        });
    }

    @Test void shipUnderConstruction_detail() throws Exception {
        Edt.run(() -> {
            Engine e = busyEngine();
            DetailPanel p = new DetailPanel(e);
            e.setSelection(Selection.ship("h1"));
            paint(p, 330, 800);
            String text = PanelSmokeTest.allText(p);
            assertTrue(text.contains("Under construction at Earth Hub (Shipyard L1): 19 days left"), text);
            assertTrue(text.contains("Cost: 30 METAL, 10 COMPONENTS · 20 days"), text);
            assertNotNull(bar(p, DetailPanel.TARGET_PROGRESS_PREFIX + "ship"));
            assertFalse(text.contains("Dispatch"), text);
        });
    }

    @Test void tankText_listsEachFuel() {
        Ship h = new Ship("h", "H", ShipClass.HAULER, null);
        assertEquals("Fuel: none", DetailPanel.tankText(h));
        h.setTank(Resource.HYDROGEN, 40);
        h.setTank(Resource.SYNFUEL, 48);
        assertEquals("Fuel: 40 hydrogen, 48 synfuel (64 value)", DetailPanel.tankText(h));
        Ship x = new Ship("x", "X", ShipClass.EXPLORER, null);
        x.setTank(Resource.HYDROGEN, 40);
        x.setTank(Resource.SYNFUEL, 48);
        assertEquals("Tank: 64 / 100 (40 hydrogen, 48 synfuel)", DetailPanel.tankText(x));
    }

    @Test void shipCard_statusCountsDown() throws Exception {
        Edt.run(() -> {
            Engine e = busyEngine();
            assertEquals("Building · 19 days left", ColonyListPanel.shipStatusText(e.world(), e.world().findShip("h1")));
        });
    }

    @Test void buildDialog_labelsAndBusyShipyard() {
        World w = WorldGenerator.generate(1L);
        Site hub = w.findSite(HUB);
        assertEquals("HAULER · 30 METAL, 10 COMPONENTS · 20 days", BuildShipDialog.classLabel(w, hub, ShipClass.HAULER));
        assertEquals("Built by Shipyard L1, ready in 40 days", BuildShipDialog.statusLine(w, hub, ShipClass.COLONIZER));
        Ship s = new Ship("h1", "Mule", ShipClass.HAULER, HUB);
        s.state = ShipState.BUILDING;
        s.buildDaysLeft = 12;
        s.yardBuildingId = hub.buildings.stream().filter(b -> b.type == BuildingType.SHIPYARD).findFirst().orElseThrow().id;
        w.ships.add(s);
        assertEquals("Every shipyard at Earth Hub is busy (Mule, 12 days left)",
            spacecolony.sim.ShipYards.whyNotBuild(w, hub, ShipClass.HAULER));
    }

    @Test void systemMap_badgesForResearchAndShipBuild_selectTheColony() throws Exception {
        Edt.run(() -> {
            Engine e = busyEngine();
            SystemMapPanel p = new SystemMapPanel(e);
            paint(p, 900, 700);
            List<SystemMapPanel.Badge> badges = p.badges();
            assertEquals(2, badges.size(), badges.toString());
            SystemMapPanel.Badge research = badges.stream().filter(b -> b.spec().kind() == ProgressBadge.Kind.RESEARCH).findFirst().orElseThrow();
            SystemMapPanel.Badge ship = badges.stream().filter(b -> b.spec().kind() == ProgressBadge.Kind.SHIP).findFirst().orElseThrow();
            assertEquals(0.4, research.spec().fraction(), 1e-9);
            assertEquals(1.0 / 20, ship.spec().fraction(), 1e-9);
            assertEquals("Earth Hub: building Mule, 19 days left", ship.spec().tooltip());
            assertFalse(research.box().intersects(ship.box()));
            // No ship marker for a ship that isn't built yet.
            assertTrue(p.shipMarks(450, 350).isEmpty());
            java.awt.Rectangle r = ship.box();
            p.dispatchEvent(new java.awt.event.MouseEvent(p, java.awt.event.MouseEvent.MOUSE_PRESSED, 0L, 0,
                (int) r.getCenterX(), (int) r.getCenterY(), 1, false, java.awt.event.MouseEvent.BUTTON1));
            assertEquals(Selection.site(HUB), e.selection());
        });
    }

    @Test void systemMap_noBadgesWhenIdle() throws Exception {
        Edt.run(() -> {
            SystemMapPanel p = new SystemMapPanel(new Engine(WorldGenerator.generate(1L)));
            paint(p, 900, 700);
            assertTrue(p.badges().isEmpty());
        });
    }
}
