package spacecolony.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import static org.junit.jupiter.api.Assertions.*;

class GlyphsTest {
    private static final int SIZE = 32;

    @Test
    void everyResourceHasItsOwnDrawing() {
        Set<String> seen = new HashSet<>();
        for (Resource r : Resource.values()) {
            assertTrue(ResourceGlyphs.hasGlyph(r), r + " falls back to a badge");
            BufferedImage img = render(g -> ResourceGlyphs.draw(g, r, 0, 0, SIZE));
            assertTrue(lit(img) > 120, r + " drew only " + lit(img) + " pixels");
            assertTrue(seen.add(fingerprint(img)), r + " looks like another resource");
        }
    }

    @Test
    void everyBuildingHasItsOwnDrawing() {
        Set<String> seen = new HashSet<>();
        for (BuildingType t : BuildingType.values()) {
            assertTrue(BuildingGlyphs.hasGlyph(t), t + " falls back to its initial");
            BufferedImage img = render(g -> BuildingGlyphs.draw(g, t, 0, 0, SIZE));
            assertTrue(lit(img) > 400, t + " drew only " + lit(img) + " pixels");
            assertTrue(seen.add(fingerprint(img)), t + " looks like another building");
        }
    }

    @Test
    void glyphsStayInsideTheirBox() {
        for (Resource r : Resource.values()) {
            BufferedImage img = new BufferedImage(SIZE * 3, SIZE * 3, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            ResourceGlyphs.draw(g, r, SIZE, SIZE, SIZE);
            g.dispose();
            assertEquals(0, litOutside(img, SIZE), r + " spills out of its box");
        }
        for (BuildingType t : BuildingType.values()) {
            BufferedImage img = new BufferedImage(SIZE * 3, SIZE * 3, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            BuildingGlyphs.draw(g, t, SIZE, SIZE, SIZE);
            g.dispose();
            assertEquals(0, litOutside(img, SIZE), t + " spills out of its box");
        }
    }

    @Test
    void fallbackBadgeAndTankDrawInTheirColour() {
        // What a new resource or fuel gets before (or instead of) its own art.
        Color band = new Color(200, 60, 220);
        BufferedImage badge = render(g -> ResourceGlyphs.inFrame(g, 0, 0, SIZE, ResourceGlyphs.badge(band, "H")));
        BufferedImage tank = render(g -> ResourceGlyphs.inFrame(g, 0, 0, SIZE, ResourceGlyphs.tank(band)));
        assertTrue(lit(badge) > 300);
        assertTrue(lit(tank) > 200);
        assertTrue(hasHueNear(tank, band), "tank band missing");
        assertEquals(ResourceGlyphs.nameColour("HELIUM3"), ResourceGlyphs.nameColour("HELIUM3"), "stable per name");
    }

    private interface Painter { void paint(Graphics2D g); }

    private static BufferedImage render(Painter p) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
        return img;
    }

    private static int lit(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) & 0xFFFFFF) != 0) n++;
        return n;
    }

    private static int litOutside(BufferedImage img, int box) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                boolean inside = x >= box - 1 && x <= 2 * box && y >= box - 1 && y <= 2 * box;
                if (!inside && (img.getRGB(x, y) & 0xFFFFFF) != 0) n++;
            }
        return n;
    }

    private static String fingerprint(BufferedImage img) {
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        return Integer.toHexString(Arrays.hashCode(px));
    }

    private static boolean hasHueNear(BufferedImage img, Color c) {
        float hue = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0];
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                if (hsb[1] > 0.4f && Math.abs(hsb[0] - hue) < 0.03f) return true;
            }
        return false;
    }
}
