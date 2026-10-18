package spacecolony.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SystemMapPanelTest {

    @Test
    void jovianMoons_drawClearOfTheirParentAndEachOther() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            SystemMapPanel p = new SystemMapPanel(engine);
            for (long tick = 0; tick < 40; tick++) {
                int[] jovian = p.screenPoint("jovian", tick, 400, 300);
                int[] io = p.screenPoint("io", tick, 400, 300);
                int[] europa = p.screenPoint("europa", tick, 400, 300);
                assertTrue(dist(jovian, io) >= SystemMapPanel.MOON_RING_MIN - 1, "Io sits on Jovian at tick " + tick);
                assertTrue(dist(jovian, europa) > dist(jovian, io), "Europa rings outside Io at tick " + tick);
                assertTrue(dist(io, europa) >= 3, "Io and Europa overlap at tick " + tick);
            }
        });
    }

    @Test
    void placeLabel_avoidsLabelsAlreadyPlaced() {
        Graphics2DFixture f = new Graphics2DFixture();
        List<Rectangle> occupied = new ArrayList<>();
        occupied.add(new Rectangle(95, 95, 10, 10)); // the dot itself
        Rectangle first = SystemMapPanel.placeLabel(f.fm, "Jovian", 100, 100, 5, occupied);
        occupied.add(first);
        Rectangle second = SystemMapPanel.placeLabel(f.fm, "Europa", 100, 100, 5, occupied);
        assertFalse(first.intersects(second), first + " vs " + second);
    }

    @Test
    void flyingShip_sitsOnItsRouteWithItsNoseTowardTheDestination() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(1L);
            w.tick = 50;
            Ship s = new Ship("x1", "Scout-1", ShipClass.EXPLORER, "site-earth-hub");
            s.state = ShipState.IN_TRANSIT;
            s.currentSiteId = null;
            s.transit = new Transit("site-earth-hub", null, "mars", 0, 100, Map.of());
            w.ships.add(s);
            SystemMapPanel p = new SystemMapPanel(new Engine(w));
            int[] from = p.screenPoint("earth", 0, 400, 300);
            int[] to = p.screenPoint("mars", 100, 400, 300);
            SystemMapPanel.ShipMark m = p.shipMarks(400, 300).get(0);
            assertTrue(m.flying());
            assertEquals((from[0] + to[0]) / 2.0, m.x(), 1.0);
            assertEquals((from[1] + to[1]) / 2.0, m.y(), 1.0);
            assertEquals(Math.atan2(to[1] - from[1], to[0] - from[0]), m.heading(), 1e-9);
        });
    }

    @Test
    void shipsDockedTogether_drawSideBySideAndClickToSelect() throws Exception {
        Edt.run(() -> {
            World w = WorldGenerator.generate(1L);
            w.ships.add(new Ship("h1", "Mule-1", ShipClass.HAULER, "site-earth-hub"));
            w.ships.add(new Ship("t1", "Gulp-1", ShipClass.TANKER, "site-earth-hub"));
            Engine engine = new Engine(w);
            SystemMapPanel p = new SystemMapPanel(engine);
            p.setSize(800, 600);
            List<SystemMapPanel.ShipMark> marks = p.shipMarks(400, 300);
            assertEquals(2, marks.size());
            var a = marks.get(0);
            var b = marks.get(1);
            assertTrue(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= SystemMapPanel.PARKED_LENGTH * 0.8, "ships overlap");
            int[] earth = p.screenPoint("earth", w.tick, 400, 300);
            assertTrue(Math.hypot(a.x() - earth[0], a.y() - earth[1]) > 6, "ship covers Earth's dot");
            p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, 0L, InputEvent.BUTTON1_DOWN_MASK,
                (int) Math.round(b.x()), (int) Math.round(b.y()), 1, false, MouseEvent.BUTTON1));
            assertEquals(Selection.ship("t1"), engine.selection());
        });
    }

    private static double dist(int[] a, int[] b) { return Math.hypot(a[0] - b[0], a[1] - b[1]); }

    private static final class Graphics2DFixture {
        final FontMetrics fm;
        Graphics2DFixture() {
            var g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
            fm = g.getFontMetrics(new Font("Dialog", Font.PLAIN, 12));
            g.dispose();
        }
    }
}
