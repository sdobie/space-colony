package spacecolony.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.JComponent;
import javax.swing.JMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.debug.DebugController;
import spacecolony.debug.DebugOverlayPanel;
import spacecolony.debug.ExceptionLog;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Debug-mode wiring through the real frame. Lives with the ui tests because it builds
 * {@link SpaceColonyFrame}; {@code debug} must not depend on {@code ui}, in tests too.
 * Needs a display (JFrame), so run the suite under xvfb-run in a headless container.
 */
class DebugControllerTest {
    @BeforeEach void needsDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; run under xvfb-run");
    }

    @Test void toggle_mountsAndUnmountsOverlayAndMenu() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            SpaceColonyFrame f = new SpaceColonyFrame(e);
            int menus = f.getJMenuBar().getMenuCount();
            e.setDebugEnabled(true);
            assertEquals(menus + 1, f.getJMenuBar().getMenuCount());
            JMenu debug = f.getJMenuBar().getMenu(menus);
            assertEquals("Debug", debug.getText());
            assertTrue(hasSubmenu(debug, "Log level"));
            assertNotNull(find(f.getContentPane(), DebugOverlayPanel.class));
            assertTrue(f.debugController().mapOverlaysOn());
            e.setDebugEnabled(false);
            assertEquals(menus, f.getJMenuBar().getMenuCount());
            assertNull(find(f.getContentPane(), DebugOverlayPanel.class));
            assertFalse(f.debugController().mapOverlaysOn());
            f.dispose();
        });
    }

    @Test void ctrlD_isBoundOnTheRootPane() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            SpaceColonyFrame f = new SpaceColonyFrame(e);
            var root = f.getRootPane();
            Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(DebugController.TOGGLE_KEY);
            assertNotNull(key);
            root.getActionMap().get(key).actionPerformed(null);
            assertTrue(e.debugEnabled());
            root.getActionMap().get(key).actionPerformed(null);
            assertFalse(e.debugEnabled());
            f.dispose();
        });
    }

    @Test void stepAndTriggerActions_followPauseState() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            SpaceColonyFrame f = new SpaceColonyFrame(e);
            e.setDebugEnabled(true);
            var actions = f.debugController().actions();
            assertTrue(actions.step.isEnabled());
            actions.step.actionPerformed(null);
            assertEquals(1, e.world().tick);
            e.setSpeed(Speed.X1);
            assertFalse(actions.step.isEnabled());
            assertFalse(actions.triggerEvent.isEnabled());
            f.gameLoop().dispose();
            f.dispose();
        });
    }

    @Test void overlay_showsStatusTimingsAndExceptionBanner() throws Exception {
        Edt.run(() -> {
            ExceptionLog log = new ExceptionLog(20);
            Engine e = new Engine(WorldGenerator.generate(1L));
            SpaceColonyFrame f = new SpaceColonyFrame(e, log);
            e.setDebugEnabled(true);
            e.advanceSilently(20);
            DebugOverlayPanel overlay = find(f.getContentPane(), DebugOverlayPanel.class);
            overlay.setSize(900, 150);
            overlay.doLayout();
            paint(overlay, 900, 150);
            assertEquals(20, f.debugController().timings().stat(spacecolony.sim.SimPhase.EVENTS).samples());
            log.add(Thread.currentThread(), new IllegalStateException("boom"));
            overlay.setStatus("hello");
            paint(overlay, 900, 150);
            f.dispose();
        });
    }

    @Test void mapOverlays_paintWithShipInTransit() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(1L);
            Ship s = new Ship("ship-h1", "H1", ShipClass.HAULER, "site-earth-hub");
            s.fuel = 1_000_000.0;
            w.ships.add(s);
            w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
            Site mars = new Site("site-mars-test", "Mars Test", "mars", 0.0, 0.0, 50);
            w.findBody("mars").sites.add(mars);
            Simulator sim = new Simulator();
            sim.enqueue(new DispatchShipCommand(s.id, mars.id, Map.of(Resource.METAL, 50.0)));
            for (int i = 0; i < 60 && s.state != ShipState.IN_TRANSIT; i++) sim.advance(w);
            assertEquals(ShipState.IN_TRANSIT, s.state);

            Engine e = new Engine(w);
            SpaceColonyFrame f = new SpaceColonyFrame(e);
            e.setDebugEnabled(true);
            SystemMapPanel map = find(f.getContentPane(), SystemMapPanel.class);
            map.setSize(900, 700);
            paint(map, 900, 700);
            f.dispose();
        });
    }

    @Test void logRecordsWithThrowables_areFormattable() throws Exception {
        // Guards the viewer's message formatting path used in the Log tab.
        LogRecord r = new LogRecord(Level.SEVERE, "value {0}");
        r.setParameters(new Object[] { 42 });
        assertEquals("value 42", new java.util.logging.SimpleFormatter().formatMessage(r));
    }

    static boolean hasSubmenu(JMenu m, String text) {
        for (int i = 0; i < m.getItemCount(); i++) {
            if (m.getItem(i) instanceof JMenu sub && text.equals(sub.getText())) return true;
        }
        return false;
    }

    static <T> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return type.cast(k);
            if (k instanceof Container kc) {
                T hit = find(kc, type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    static void paint(JComponent c, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            assertDoesNotThrow(() -> c.paint(g));
        } finally {
            g.dispose();
        }
    }
}
