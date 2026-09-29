package spacecolony.ui;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class BuildingMenuTest {
    private Engine engine;
    private Site hub;
    private final java.util.function.BiPredicate<Component, String> realConfirm = BuildingMenu.confirm;

    @BeforeEach
    void setUp() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; run under xvfb-run");
        engine = new Engine(WorldGenerator.generate(42L));
        hub = engine.world().findSite("site-earth-hub");
    }

    @AfterEach
    void restore() { BuildingMenu.confirm = realConfirm; }

    private static JMenuItem item(JPopupMenu m, String name) {
        for (Component c : m.getComponents()) if (c instanceof JMenuItem i && name.equals(i.getName())) return i;
        return null;
    }

    private Building farm() {
        return hub.buildings.stream().filter(b -> b.type == BuildingType.FARM).findFirst().orElseThrow();
    }

    @Test
    void finishedBuildingOffersUpgradeAndDemolish() throws Exception {
        Edt.run(() -> {
            JPopupMenu m = BuildingMenu.create(engine, hub, farm());
            JMenuItem up = item(m, BuildingMenu.UPGRADE);
            assertEquals("Upgrade to L2 · 15 METAL, 5 COMPONENTS · 3 days", up.getText());
            assertTrue(up.isEnabled());
            assertTrue(up.getToolTipText().contains("FOOD +1.5 a day"), up.getToolTipText());
            assertEquals("Demolish… · refund 7 METAL, 2 COMPONENTS", item(m, BuildingMenu.DEMOLISH).getText());
            assertNull(item(m, BuildingMenu.REPAIR));
            assertNull(item(m, BuildingMenu.CANCEL));
            up.doClick();
            assertEquals(1, engine.commandQueueDepth());
        });
    }

    @Test
    void buildingUnderConstructionOffersOnlyCancel() throws Exception {
        Edt.run(() -> {
            Building b = hub.addBuilding(new Building(BuildingType.FARM, 0));
            b.daysLeft = 2;
            JPopupMenu m = BuildingMenu.create(engine, hub, b);
            assertEquals(1, m.getComponentCount());
            JMenuItem cancel = item(m, BuildingMenu.CANCEL);
            assertEquals("Cancel construction · refund 15 METAL, 5 COMPONENTS", cancel.getText());
            cancel.doClick();
            assertEquals(1, engine.commandQueueDepth());
        });
    }

    @Test
    void damagedBuildingOffersRepair_disabledWhenShort() throws Exception {
        Edt.run(() -> {
            Building mine = hub.buildings.stream().filter(b -> b.type == BuildingType.MINE).findFirst().orElseThrow();
            mine.enabled = false;
            JMenuItem repair = item(BuildingMenu.create(engine, hub, mine), BuildingMenu.REPAIR);
            assertTrue(repair.isEnabled());
            assertFalse(item(BuildingMenu.create(engine, hub, mine), BuildingMenu.UPGRADE).isEnabled());
            hub.stockpile.put(Resource.METAL, 0.0);
            repair = item(BuildingMenu.create(engine, hub, mine), BuildingMenu.REPAIR);
            assertFalse(repair.isEnabled());
            assertEquals("Need 5 more METAL (have 0 of 5)", repair.getToolTipText());
        });
    }

    @Test
    void demolishAsksFirst() throws Exception {
        Edt.run(() -> {
            String[] asked = new String[1];
            BuildingMenu.confirm = (parent, msg) -> { asked[0] = msg; return false; };
            item(BuildingMenu.create(engine, hub, farm()), BuildingMenu.DEMOLISH).doClick();
            assertTrue(asked[0].startsWith("Demolish Farm L1 at Earth Hub?"), asked[0]);
            assertEquals(0, engine.commandQueueDepth());
            BuildingMenu.confirm = (parent, msg) -> true;
            item(BuildingMenu.create(engine, hub, farm()), BuildingMenu.DEMOLISH).doClick();
            assertEquals(1, engine.commandQueueDepth());
        });
    }

    @Test
    void refundOverTheCapIsFlagged() {
        hub.stockpile.put(Resource.METAL, 995.0);
        String note = BuildingMenu.lostNote(hub, java.util.Map.of(Resource.METAL, 7.0));
        assertTrue(note.contains("2 METAL would be lost: storage full."), note);
    }
}
