package spacecolony.debug;

import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.JComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.sim.EventKind;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Inspector, log viewer and trigger-dialog behaviour without showing windows. Needs a display. */
class DebugDialogsTest {
    @BeforeEach void needsDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; run under xvfb-run");
    }

    @Test void inspector_editsWhilePaused_refreshesAndClosesOnReplace() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            var site = e.world().findSite("site-earth-hub");
            ObjectInspectorDialog d = new ObjectInspectorDialog(null, e, site, "Site site-earth-hub");
            paint((JComponent) d.getContentPane(), 520, 640);
            ObjectTreeModel.Node pop = d.model().child(d.model().root(), "population");
            assertTrue(d.editToggle().isEnabled());
            assertTrue(d.commit(pop, "4321"));
            assertEquals(4321, site.population);
            // WorldChanged from the edit refreshed the model.
            assertEquals(4321, d.model().find(pop.path()).value());

            e.setSpeed(Speed.X1);
            assertFalse(d.editToggle().isEnabled(), "edits are paused-only");
            e.setSpeed(Speed.PAUSED);

            assertTrue(d.isDisplayable());
            e.reset(WorldGenerator.generate(2L));
            assertFalse(d.isDisplayable(), "inspector closes when the world is replaced");
        });
    }

    @Test void inspector_keepsExpandedRowsAcrossRefresh() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            ObjectInspectorDialog d = new ObjectInspectorDialog(null, e, e.world(), "World");
            ObjectTreeModel m = d.model();
            ObjectTreeModel.Node bodies = m.child(m.root(), "bodies");
            d.tree().expandPath(m.treePath(bodies));
            e.step();
            ObjectTreeModel.Node again = m.find(bodies.path());
            assertTrue(d.tree().isExpanded(m.treePath(again)));
            d.dispose();
        });
    }

    @Test void logViewer_filtersAndShowsStack() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            RingBufferHandler ring = new RingBufferHandler(50);
            ring.publish(rec(Level.FINE, "spacecolony.engine", "fine engine", null));
            ring.publish(rec(Level.INFO, "spacecolony.sim.events", "info event", null));
            ring.publish(rec(Level.SEVERE, "spacecolony.crash", "Uncaught", new IllegalStateException("kaput")));
            LogViewerDialog d = new LogViewerDialog(null, e, ring, Level.ALL);
            assertEquals(3, d.logTable().getRowCount());
            d.minLevelBox().setSelectedItem(Level.INFO);
            assertEquals(2, d.logTable().getRowCount());
            d.loggerPrefixField().setText("sim");
            assertEquals(1, d.logTable().getRowCount());
            assertEquals("sim.events", d.logTable().getValueAt(0, 2));
            d.loggerPrefixField().setText("");
            d.minLevelBox().setSelectedItem(Level.ALL);
            d.logTable().setRowSelectionInterval(2, 2);
            assertTrue(d.stackPaneText().contains("IllegalStateException: kaput"));
            assertTrue(d.visibleText().contains("\tSEVERE\tcrash\tUncaught"));
            paint((JComponent) d.getContentPane(), 900, 520);
            d.dispose();
        });
    }

    @Test void logViewer_eventsTabFiltersByKind() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            TriggerEventDialog.trigger(e, "earth", EventKind.SOLAR_FLARE);
            TriggerEventDialog.trigger(e, "mars", EventKind.METEOR_STRIKE);
            LogViewerDialog d = new LogViewerDialog(null, e, new RingBufferHandler(5), Level.ALL);
            int all = d.eventRowCount();
            assertTrue(all >= 2);
            d.kindBox().setSelectedItem(EventKind.METEOR_STRIKE);
            assertEquals(1, d.eventRowCount());
            d.tabs().setSelectedIndex(1);
            assertTrue(d.visibleText().contains("METEOR_STRIKE"));
            TriggerEventDialog.trigger(e, "mars", EventKind.METEOR_STRIKE);
            assertEquals(2, d.eventRowCount(), "Events tab follows WorldChanged");
            d.dispose();
        });
    }

    @Test void triggerDialog_listsBodiesWithSitesFirst() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            var choices = TriggerEventDialog.bodyChoices(e);
            assertEquals(e.world().bodies.size(), choices.size());
            assertEquals("earth", choices.get(0).body().id);
            assertEquals("Earth (earth)", choices.get(0).toString());
        });
    }

    private static LogRecord rec(Level l, String logger, String msg, Throwable t) {
        LogRecord r = new LogRecord(l, msg);
        r.setLoggerName(logger);
        r.setThrown(t);
        return r;
    }

    private static void paint(JComponent c, int w, int h) {
        c.setSize(w, h);
        c.doLayout();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            assertDoesNotThrow(() -> c.paint(g));
        } finally {
            g.dispose();
        }
    }
}
