package spacecolony.ui.debug;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import spacecolony.debug.DebugController;
import spacecolony.debug.ObjectInspector;
import spacecolony.debug.RingBufferHandler;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class DebugPanelsTest {

    @Test
    void overlay_showsStatsAndGatesStepOnPause() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            RingBufferHandler buf = new RingBufferHandler(50);
            DebugOverlayPanel p = new DebugOverlayPanel(dc, buf);
            p.setSize(1200, 80);
            paint(p, 1200, 80);
            assertTrue(p.statsText().startsWith("Tick 0"));
            assertTrue(p.stepEnabled());
            assertEquals("Exceptions: none", p.exceptionsText());

            dc.runTicks(10);
            assertTrue(p.statsText().startsWith("Tick 10"));
            engine.setSpeed(Speed.X1);
            assertFalse(p.stepEnabled());

            LogRecord r = new LogRecord(Level.SEVERE, "boom");
            r.setThrown(new IllegalStateException("kaput"));
            buf.publish(r);
            p.refresh();
            assertTrue(p.exceptionsText().contains("IllegalStateException: kaput"), p.exceptionsText());
            dc.dispose();
        });
    }

    @Test
    void debugUi_hidesMenuAndOverlayUntilDebugIsOn() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugUi ui = new DebugUi(engine);
            assertFalse(ui.menu().isVisible());
            assertFalse(ui.overlay().isVisible());
            engine.setDebugEnabled(true);
            assertTrue(ui.menu().isVisible());
            assertTrue(ui.overlay().isVisible());
            engine.setDebugEnabled(false);
            assertFalse(ui.overlay().isVisible());
            ui.controller().dispose();
        });
    }

    @Test
    void debugUi_ctrlDTogglesDebugMode() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugUi ui = new DebugUi(engine);
            javax.swing.JRootPane root = new javax.swing.JRootPane();
            ui.installKeyBinding(root);
            Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(DebugUi.TOGGLE_KEY);
            assertNotNull(key);
            root.getActionMap().get(key).actionPerformed(null);
            assertTrue(engine.debugEnabled());
            root.getActionMap().get(key).actionPerformed(null);
            assertFalse(engine.debugEnabled());
            ui.controller().dispose();
        });
    }

    @Test
    void inspector_isReadOnlyUntilEditIsTicked_thenReinjects() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            ObjectInspectorPanel p = new ObjectInspectorPanel(engine, Selection.site("site-earth-hub"));
            p.setSize(500, 600);
            paint(p, 500, 600);

            select(p, "population");
            assertFalse(p.setButton().isEnabled(), "read-only by default");

            p.editToggle().doClick();
            assertTrue(p.setButton().isEnabled());
            p.valueField().setText("4321");
            p.setButton().doClick();
            assertEquals(4321, engine.world().findSite("site-earth-hub").population);
            assertTrue(p.statusText().contains("population"));

            // After the rebuild the same row stays selected, showing the new value.
            assertEquals("4321", p.valueField().getText());

            p.valueField().setText("not a number");
            p.setButton().doClick();
            assertEquals(4321, engine.world().findSite("site-earth-hub").population);
            assertTrue(p.statusText().startsWith("Not a valid"), p.statusText());
        });
    }

    @Test
    void inspector_refusesEditsWhileRunning() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            ObjectInspectorPanel p = new ObjectInspectorPanel(engine, Selection.site("site-earth-hub"));
            select(p, "population");
            p.editToggle().doClick();
            engine.setSpeed(Speed.X1);
            int before = engine.world().findSite("site-earth-hub").population;
            p.valueField().setText("999");
            p.setButton().doClick();
            assertEquals(before, engine.world().findSite("site-earth-hub").population);
            assertTrue(p.statusText().startsWith("Pause"), p.statusText());
        });
    }

    @Test
    void inspector_handlesVanishedTarget() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            ObjectInspectorPanel p = new ObjectInspectorPanel(engine, Selection.ship("ghost"));
            assertTrue(p.statusText().contains("no longer exists"));
        });
    }

    @Test
    void logViewer_filtersByLevelAndSource() throws Exception {
        Edt.run(() -> {
            RingBufferHandler buf = new RingBufferHandler(50);
            buf.publish(record(Level.FINE, "spacecolony.engine.Engine", "fine engine"));
            buf.publish(record(Level.INFO, "spacecolony.sim.events", "info event"));
            buf.publish(record(Level.WARNING, "spacecolony.engine.Engine", "warn engine"));

            assertEquals(3, LogViewerPanel.render(buf, Level.ALL, "").lines().count());
            String warnOnly = LogViewerPanel.render(buf, Level.WARNING, "");
            assertTrue(warnOnly.contains("warn engine") && !warnOnly.contains("info event"));
            String sim = LogViewerPanel.render(buf, Level.ALL, "sim");
            assertTrue(sim.contains("info event") && !sim.contains("engine"));

            LogViewerPanel p = new LogViewerPanel(buf);
            p.minLevelBox().setSelectedItem(Level.INFO);
            p.sourceField().setText("spacecolony.engine");
            assertTrue(p.shownText().contains("warn engine"));
            assertFalse(p.shownText().contains("fine engine"));
            assertFalse(p.shownText().contains("info event"));
        });
    }

    @Test
    void logViewer_includesStackTraces() {
        LogRecord r = record(Level.SEVERE, "spacecolony.uncaught", "Uncaught");
        r.setThrown(new RuntimeException("trace me"));
        String s = LogViewerPanel.format(r);
        assertTrue(s.contains("SEVERE"));
        assertTrue(s.contains("java.lang.RuntimeException: trace me"));
        assertTrue(s.contains("\tat "));
    }

    @Test
    void triggerEventDialog_defaultsToSelectedBodyOrSiteBody() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            assertNull(TriggerEventDialog.defaultBodyId(engine));
            engine.setSelection(Selection.body("mars"));
            assertEquals("mars", TriggerEventDialog.defaultBodyId(engine));
            engine.setSelection(Selection.site("site-earth-hub"));
            assertEquals("earth", TriggerEventDialog.defaultBodyId(engine));
        });
    }

    private static LogRecord record(Level l, String logger, String msg) {
        LogRecord r = new LogRecord(l, msg);
        r.setLoggerName(logger);
        return r;
    }

    private static void select(ObjectInspectorPanel p, String childName) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) p.tree().getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode c = (DefaultMutableTreeNode) root.getChildAt(i);
            if (((ObjectInspector.Node) c.getUserObject()).name().equals(childName)) {
                p.tree().setSelectionPath(new TreePath(c.getPath()));
                return;
            }
        }
        fail("no child " + childName);
    }

    private static void paint(JComponent c, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            assertDoesNotThrow(() -> c.paint(g));
        } finally {
            g.dispose();
        }
    }
}
