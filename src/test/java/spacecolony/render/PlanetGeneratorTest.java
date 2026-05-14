package spacecolony.render;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import static org.junit.jupiter.api.Assertions.*;

class PlanetGeneratorTest {

    @Test
    void sameSeed_producesIdenticalImage() {
        BufferedImage a = new PlanetGenerator(256, 128).generate(42L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage b = new PlanetGenerator(256, 128).generate(42L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertPixelEqual(a, b);
    }

    @Test
    void differentSeeds_produceDifferentImages() {
        BufferedImage a = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage b = new PlanetGenerator(128, 64).generate(2L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertFalse(pixelsEqual(a, b), "Different seeds should produce different images");
    }

    @Test
    void rockyBody_hasOceanColors() {
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.ROCKY));
        // Earth ocean is blue-dominant: b > r and b > g.
        long oceanish = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                if (b > 100 && b > r && b > g) oceanish++;
            }
        }
        assertTrue(oceanish > 100, "Rocky body should have significant blue ocean pixels");
    }

    @Test
    void asteroid_hasNoOceanColors() {
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.ASTEROID));
        long blueish = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                if (b > 100 && b > r && b > g + 20) blueish++;
            }
        }
        assertEquals(0, blueish, "Asteroid should have no blue ocean pixels");
    }

    @Test
    void gasGiant_predominantlyTanPalette() {
        // Gas giant uses the JOVIAN_BANDS palette (warm tan/orange tones).
        // Verify average pixel is in the tan family (R > B, G > B).
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.GAS_GIANT));
        long rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                rSum += (rgb >> 16) & 0xff;
                gSum += (rgb >> 8) & 0xff;
                bSum += rgb & 0xff;
                count++;
            }
        }
        double rAvg = (double) rSum / count;
        double gAvg = (double) gSum / count;
        double bAvg = (double) bSum / count;
        assertTrue(rAvg > bAvg + 20, "Gas giant should be warm-toned (R - B > 20): R=" + rAvg + " B=" + bAvg);
        assertTrue(gAvg > bAvg + 10, "Gas giant should be warm-toned (G - B > 10): G=" + gAvg + " B=" + bAvg);
    }

    @Test
    void dimensions_matchConstructor() {
        BufferedImage img = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertEquals(128, img.getWidth());
        assertEquals(64, img.getHeight());
    }

    private static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    private static void assertPixelEqual(BufferedImage a, BufferedImage b) {
        assertTrue(pixelsEqual(a, b), "Images must be pixel-identical");
    }
}
