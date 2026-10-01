package spacecolony.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.save.SaveSlots;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class PanelSmokeTest {

    @Test
    void fileMenu_buildsWithoutCrashing(@TempDir Path tmp) throws Exception {
        // Null owner: it is only used as the parent component for modal dialogs, which
        // this test never opens. Constructing the menu exercises the action wiring.
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            GameSession session = new GameSession(engine, new SaveSlots(tmp), NO_UI, code -> {});
            FileMenu menu = new FileMenu(null, engine, session);
            assertEquals(1, menu.getMenuCount());
            assertEquals("File", menu.getMenu(0).getText());
            assertEquals(7, menu.getMenu(0).getMenuComponentCount(),
                "New/Save/Save As/Load/Load from file/separator/Quit");

            FileMenu withOptions = new FileMenu(null, engine, session, () -> {});
            assertEquals(9, withOptions.getMenu(0).getMenuComponentCount(),
                "… separator/Options…/separator/Quit");

            GameSession menuSession = new GameSession(engine, new SaveSlots(tmp), NO_UI, code -> {},
                () -> {}, () -> true);
            FileMenu full = new FileMenu(null, engine, menuSession, () -> {});
            assertEquals(10, full.getMenu(0).getMenuComponentCount(),
                "… separator/Options…/Main Menu/separator/Quit");
            assertEquals("Main Menu", ((javax.swing.JMenuItem) full.getMenu(0).getMenuComponent(7)).getText());
        });
    }

    @Test
    void fileMenu_tutorialMode_disablesSaving(@TempDir Path tmp) throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            GameSession session = new GameSession(engine, new SaveSlots(tmp), NO_UI, code -> {});
            FileMenu menu = new FileMenu(null, engine, session);
            javax.swing.JMenuItem save = (javax.swing.JMenuItem) menu.getMenu(0).getMenuComponent(1);
            javax.swing.JMenuItem saveAs = (javax.swing.JMenuItem) menu.getMenu(0).getMenuComponent(2);
            assertTrue(save.isEnabled());
            session.setMode(GameSession.Mode.TUTORIAL);
            assertFalse(save.isEnabled());
            assertFalse(saveAs.isEnabled());
            assertEquals("Not available during the tutorial", save.getToolTipText());
            session.setMode(GameSession.Mode.NORMAL);
            assertTrue(saveAs.isEnabled());
        });
    }

    @Test
    void autosaveTimer_setMinutes() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            AutosaveTimer t = new AutosaveTimer(new GameSession(engine, new SaveSlots(Path.of("unused")), NO_UI, c -> {}));
            t.setMinutes(5);
            assertTrue(t.running());
            assertEquals(300_000, t.delayMillis());
            t.setMinutes(0);
            assertFalse(t.running());
        });
    }

    static final GameSession.Ui NO_UI = new GameSession.Ui() {
        public boolean confirmQuit(GameSession.Mode mode, boolean toMenu) { return false; }
        public boolean quitAnyway(String msg) { return false; }
        public void toast(String text) {}
    };

    @Test
    void sphereMiniRenderer_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            SphereMiniRenderer panel = new SphereMiniRenderer(engine);
            panel.setBody(engine.world().findBody("earth"));
            panel.setSize(200, 200);
            paintToImage(panel, 200, 200);
        });
    }

    @Test
    void topBar_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            TopBar p = new TopBar(engine);
            p.toast("Saved “colony”");
            assertEquals("Saved “colony”", p.statusText());
            p.setSize(800, 40);
            paintToImage(p, 800, 40);
        });
    }

    @Test
    void colonyListPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            ColonyListPanel p = new ColonyListPanel(engine);
            p.setSize(220, 600);
            paintToImage(p, 220, 600);
        });
    }

    @Test
    void systemMapPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            SystemMapPanel p = new SystemMapPanel(engine);
            p.setSize(800, 600);
            paintToImage(p, 800, 600);
        });
    }

    @Test
    void detailPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DetailPanel p = new DetailPanel(engine);
            p.setSize(280, 600);
            paintToImage(p, 280, 600);
            engine.setSelection(Selection.body("earth"));
            paintToImage(p, 280, 600);
            engine.setSelection(Selection.site("site-earth-hub"));
            paintToImage(p, 280, 600);
        });
    }

    @Test
    void detailPanel_siteShowsCapBreakdownAndNetRates() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.world().tech.researched.add("colony-mgmt-i");
            engine.tick();
            DetailPanel p = new DetailPanel(engine);
            engine.setSelection(Selection.site("site-earth-hub"));
            p.setSize(280, 600);
            paintToImage(p, 280, 600);
            String text = allText(p);
            assertTrue(text.contains("base 200 + habitats 100 × 1.20"), text);
            assertTrue(text.contains("Resources"), text);
            assertTrue(text.contains("Farm L1"), text);
        });
    }

    @Test
    void detailPanel_everyBuildingTypeAndADisabledOne() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            var hub = engine.world().findSite("site-earth-hub");
            for (var t : spacecolony.sim.BuildingType.values()) hub.buildings.add(new spacecolony.sim.Building(t, 1));
            hub.buildings.get(hub.buildings.size() - 1).enabled = false;
            DetailPanel p = new DetailPanel(engine);
            engine.setSelection(Selection.site("site-earth-hub"));
            p.setSize(330, 900);
            paintToImage(p, 330, 900);
            assertTrue(allText(p).contains("(estimate)"), "no tick yet: estimate");
            engine.tick();
            paintToImage(p, 330, 900);
            String text = allText(p);
            assertTrue(text.contains("Research lab L1   damaged: repair 5 METAL, 3 COMPONENTS"), text);
            assertTrue(text.contains("Refinery L1   65%: short of ICE"), text);
        });
    }

    @Test
    void detailPanel_constructionUpgradesAndDamage() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.world().randomEventsEnabled = false;
            var hub = engine.world().findSite("site-earth-hub");
            var farm = hub.addBuilding(new spacecolony.sim.Building(spacecolony.sim.BuildingType.FARM, 0));
            farm.daysLeft = 4;
            hub.buildings.get(2).daysLeft = 3; // the mine, upgrading
            hub.buildings.get(4).enabled = false; // the shipyard, damaged
            engine.tick();
            DetailPanel p = new DetailPanel(engine);
            engine.setSelection(Selection.site("site-earth-hub"));
            p.setSize(330, 900);
            paintToImage(p, 330, 900);
            String text = allText(p);
            assertTrue(text.contains("Buildings  6 of 10 slots"), text);
            assertTrue(text.contains("Farm   building, 3 days left"), text);
            assertTrue(text.contains("Mine L1 → L2"), text);
            assertTrue(text.contains("(upgrading, 2 d)"), text);
            assertTrue(text.contains("Shipyard L1   damaged: repair 13 METAL, 5 COMPONENTS"), text);
            javax.swing.JButton more = find(p, javax.swing.JButton.class,
                b -> (DetailPanel.TARGET_BUILDING_PREFIX + farm.id).equals(b.getName()));
            assertNotNull(more);
        });
    }

    @Test
    void detailPanel_longBuildingRowsWrapInsideTheColumn() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            engine.world().randomEventsEnabled = false;
            var hub = engine.world().findSite("site-earth-hub");
            hub.buildings.get(2).daysLeft = 3; // the mine, upgrading: its row is the longest
            engine.tick();
            DetailPanel p = new DetailPanel(engine);
            engine.setSelection(Selection.site("site-earth-hub"));
            p.setSize(330, 600);
            layoutTree(p);
            layoutTree(p); // wrapped text settles its height on the second pass
            paintToImage(p, 330, 600);
            javax.swing.JViewport viewport = find(p, javax.swing.JViewport.class, v -> true);
            assertEquals(viewport.getWidth(), viewport.getView().getWidth(), "column content wider than the column");
            javax.swing.JTextArea mine = find(p, javax.swing.JTextArea.class, a -> a.getText().startsWith("Mine L1"));
            assertNotNull(mine, allText(p));
            assertTrue(mine.getHeight() > mine.getFontMetrics(mine.getFont()).getHeight(), "mine row should wrap: " + mine.getText());
            for (var b : hub.buildings) {
                javax.swing.JButton more = find(p, javax.swing.JButton.class,
                    x -> (DetailPanel.TARGET_BUILDING_PREFIX + b.id).equals(x.getName()));
                java.awt.Rectangle r = javax.swing.SwingUtilities.convertRectangle(more.getParent(), more.getBounds(), p);
                assertTrue(r.x >= 0 && r.x + r.width <= p.getWidth(), b.type + " ⋯ button outside the column: " + r);
            }
        });
    }

    private static void layoutTree(java.awt.Component c) {
        if (!(c instanceof java.awt.Container k)) return;
        k.doLayout();
        for (java.awt.Component child : k.getComponents()) layoutTree(child);
    }

    @Test
    void bodyViewPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            BodyViewPanel p = new BodyViewPanel(engine);
            p.setSize(600, 600);
            paintToImage(p, 600, 600);
            engine.setSelection(Selection.body("earth"));
            paintToImage(p, 600, 600);
        });
    }

    @Test
    void eventStripPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            EventStripPanel p = new EventStripPanel(engine);
            p.setSize(800, 110);
            paintToImage(p, 800, 110);
        });
    }

    @Test
    void eventStrip_filtersBySeverity_andClickSelects() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            EventStripPanel p = new EventStripPanel(engine);
            engine.world().emit(new spacecolony.sim.Event(1, spacecolony.sim.EventSeverity.WARNING,
                spacecolony.sim.EventKind.METEOR_STRIKE, "Meteor strike on Mars", "mars", null, null));
            engine.world().emit(spacecolony.sim.Event.info(1, spacecolony.sim.EventKind.RESEARCH_COMPLETED, "Researched X"));
            engine.tick();
            int before = p.rows().size();
            p.filter(spacecolony.sim.EventSeverity.INFO).doClick();
            java.util.List<javax.swing.JLabel> rows = p.rows();
            assertTrue(rows.size() < before);
            assertTrue(rows.stream().allMatch(l -> !l.getText().contains("Researched X")));
            javax.swing.JLabel meteor = rows.stream().filter(l -> l.getText().contains("Meteor strike on Mars"))
                .findFirst().orElseThrow();
            meteor.dispatchEvent(new java.awt.event.MouseEvent(meteor, java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 1, 1, 1, false));
            assertEquals(Selection.body("mars"), engine.selection());
            p.filter(spacecolony.sim.EventSeverity.WARNING).doClick();
            p.filter(spacecolony.sim.EventSeverity.ERROR).doClick();
            assertEquals("No matching events.", p.rows().get(0).getText());
        });
    }

    @Test
    void techAndGoalsModalContents_paintWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.world().tech.researched.add("ion-drives");
            engine.world().tech.researched.add("life-support-i");
            engine.world().tech.activeId = "fusion-drives";
            engine.world().findSite("site-earth-hub").buildings.add(
                new spacecolony.sim.Building(spacecolony.sim.BuildingType.RESEARCH_LAB, 1));
            JPanel techList = TechModal.buildList(engine, () -> {});
            assertEquals(spacecolony.sim.TechCatalog.all().size() + 3, techList.getComponentCount(),
                "one row per tech plus Tier 0-2 headers");
            String text = allText(techList);
            assertTrue(text.contains("Tier 2"), text);
            assertTrue(text.contains("needs Fusion Drives"), text);
            assertTrue(progressBarText(techList).contains("ETA"), progressBarText(techList));
            techList.setSize(540, 2000);
            techList.doLayout();
            paintToImage(techList, 540, 2000);
            JPanel techHeader = TechModal.header(engine.world());
            techHeader.setSize(540, 30);
            paintToImage(techHeader, 540, 30);
            assertNotNull(GoalsModal.header(engine).getText());
            JPanel goalList = GoalsModal.buildList(engine.world());
            goalList.setSize(480, 1200);
            goalList.doLayout();
            paintToImage(goalList, 480, 1200);
            String goalText = allText(goalList);
            assertTrue(goalText.contains("Expansion") && goalText.contains("Population") && goalText.contains("Fleet"), goalText);
            assertTrue(progressBarText(goalList).contains("1 / 5 bodies"), progressBarText(goalList));
        });
    }

    /** Text of every JLabel under {@code c}, one per line. */
    /** Text of every JButton under {@code c}. */
    static java.util.List<String> buttonTexts(java.awt.Component c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (c instanceof javax.swing.AbstractButton b && b.getText() != null) out.add(b.getText());
        if (c instanceof java.awt.Container k) for (java.awt.Component child : k.getComponents()) out.addAll(buttonTexts(child));
        return out;
    }

    @Test
    void detailPanel_orbitingColonizer_offersFoundColonyNotDispatch() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            spacecolony.sim.Ship c = new spacecolony.sim.Ship("c1", "Ark", spacecolony.sim.ShipClass.COLONIZER, null);
            c.orbitingBodyId = "mars";
            engine.world().ships.add(c);
            DetailPanel p = new DetailPanel(engine);
            engine.setSelection(Selection.ship("c1"));
            p.setSize(280, 600);
            paintToImage(p, 280, 600);
            assertTrue(allText(p).contains("Orbiting Mars"), allText(p));
            assertTrue(buttonTexts(p).contains("Found colony…"), buttonTexts(p).toString());
            assertFalse(buttonTexts(p).contains("Dispatch..."));
            assertEquals("Ark  · orbiting Mars", ColonyListPanel.shipRowText(engine.world(), c));
        });
    }

    @SuppressWarnings("unchecked")
    static <T extends java.awt.Component> T find(java.awt.Component c, Class<T> type, java.util.function.Predicate<T> p) {
        if (type.isInstance(c) && p.test((T) c)) return (T) c;
        if (c instanceof java.awt.Container k)
            for (java.awt.Component child : k.getComponents()) {
                T hit = find(child, type, p);
                if (hit != null) return hit;
            }
        return null;
    }

    static String allText(java.awt.Component c) {
        StringBuilder sb = new StringBuilder();
        if (c instanceof javax.swing.JLabel l && l.getText() != null) sb.append(l.getText()).append('\n');
        if (c instanceof javax.swing.JTextArea a && a.getText() != null) sb.append(a.getText()).append('\n');
        if (c instanceof java.awt.Container k) for (java.awt.Component child : k.getComponents()) sb.append(allText(child));
        return sb.toString();
    }

    /** Strings painted on every JProgressBar under {@code c}. */
    static String progressBarText(java.awt.Component c) {
        StringBuilder sb = new StringBuilder();
        if (c instanceof javax.swing.JProgressBar b && b.getString() != null) sb.append(b.getString()).append('\n');
        if (c instanceof java.awt.Container k) for (java.awt.Component child : k.getComponents()) sb.append(progressBarText(child));
        return sb.toString();
    }

    private static void paintToImage(JPanel panel, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            assertDoesNotThrow(() -> panel.paint(g));
        } finally {
            g.dispose();
        }
    }
}
