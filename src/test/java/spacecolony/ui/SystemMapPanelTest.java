package spacecolony.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
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
