package spacecolony.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.ShipClass;
import static org.junit.jupiter.api.Assertions.*;

class ShipSpritesTest {
    @Test
    void everyShipClassDrawsInItsAccentColour() {
        for (ShipClass cls : ShipClass.values()) {
            BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            ShipSprites.draw(g, cls, 20, 20, 0, 30, false);
            g.dispose();
            int accent = ShipSprites.accent(cls).getRGB() & 0xFFFFFF;
            int lit = 0;
            boolean hasAccent = false;
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 40; x++) {
                    int rgb = img.getRGB(x, y) & 0xFFFFFF;
                    if (rgb != 0) lit++;
                    if (rgb == accent) hasAccent = true;
                }
            }
            assertTrue(lit > 60, cls + " drew only " + lit + " pixels");
            assertTrue(hasAccent, cls + " shows no " + new Color(accent));
        }
    }

    @Test
    void thrustAddsAFlameBehindTheStern() {
        // Stern at x = 28; the flame reaches back toward x = 19.
        assertEquals(0, redBehindStern(false));
        assertTrue(redBehindStern(true) > 60, "no flame behind the stern");
    }

    private static int redBehindStern(boolean thrusting) {
        BufferedImage img = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ShipSprites.draw(g, ShipClass.HAULER, 40, 20, 0, 24, thrusting);
        g.dispose();
        return (img.getRGB(24, 20) >> 16) & 0xFF;
    }
}
