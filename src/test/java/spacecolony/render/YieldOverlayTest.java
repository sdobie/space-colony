package spacecolony.render;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §5: the overlay colours by yield band, in the renderer's map coordinates. */
class YieldOverlayTest {
    @Test void pixelMapping_matchesTheSphereRenderer() {
        // Render a map whose only bright column is x = 100 (35°E, facing the camera) and check the globe shows it where
        // unproject says that longitude is.
        int w = 1024, h = 512;
        BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 98; x <= 102; x++) flat.setRGB(x, y, 0xFFFFFF);
        int size = 400;
        BufferedImage sphere = SphereRenderer.render(flat, size, 0.0, 0.0, 1.0, 1L, null);
        int found = 0;
        for (int py = 150; py < 250; py += 10) for (int px = 20; px < size - 20; px++) {
            if ((sphere.getRGB(px, py) & 0xFF) < 120) continue;
            double[] ll = SphereRenderer.unproject(px, py, size, 0.0, 0.0, 1.0);
            if (ll == null) continue;
            double lon = (ll[1] + 2 * Math.PI) % (2 * Math.PI);
            assertEquals(YieldOverlay.lonAt(100, w), lon, 2 * Math.PI * 4 / w, "pixel " + px + "," + py);
            assertEquals(ll[0], YieldOverlay.latAt((int) ((0.5 - ll[0] / Math.PI) * h), h), Math.PI / h * 1.5);
            found++;
        }
        assertTrue(found > 0, "the bright column should be visible");
    }

    @Test void richGroundIsTheResourceColour_poorGroundIsDarkGrey() {
        BufferedImage flat = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) flat.setRGB(x, y, 0xC0603A); // Mars-ish
        BufferedImage none = YieldOverlay.tint(flat, (r, lat, lon) -> 0.0, Resource.ORE);
        BufferedImage rich = YieldOverlay.tint(flat, (r, lat, lon) -> 1.0, Resource.ORE);
        assertEquals(8, rich.getWidth());
        assertEquals(4, rich.getHeight());
        assertEquals(YieldOverlay.colorOf(Resource.ORE).getRGB() & 0xFFFFFF, rich.getRGB(3, 2) & 0xFFFFFF);
        int n = none.getRGB(3, 2);
        int nr = (n >> 16) & 0xFF, ng = (n >> 8) & 0xFF, nb = n & 0xFF;
        assertTrue(nr < 0x60 && nr == ng && ng == nb, "poor ground is plain dark grey: " + Integer.toHexString(n));
    }

    @Test void bandsMatchTheSurveyRatings() {
        for (double y = 0; y <= 1.0; y += 0.01) {
            int band = YieldOverlay.band(y);
            ResourceSurvey.Rating rating = ResourceSurvey.rating(y);
            assertEquals(ResourceSurvey.Rating.values().length - 1 - rating.ordinal(), band, "yield " + y);
        }
    }

    @Test void eachBandIsVisiblyStrongerThanTheLast() {
        for (Resource r : ResourceSurvey.SURVEYED) {
            int prev = -1000;
            for (int b = 0; b < 4; b++) {
                java.awt.Color c = YieldOverlay.swatch(r, b);
                int dist = Math.abs(c.getRed() - 0x26) + Math.abs(c.getGreen() - 0x26) + Math.abs(c.getBlue() - 0x26);
                assertTrue(dist > prev + 40, r + " band " + b + " " + c);
                prev = dist;
            }
        }
    }

    @Test void tint_followsTheSampler_withAnOutlineOnTheBandEdge() {
        BufferedImage flat = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        // Yield 1 in the northern hemisphere only.
        BufferedImage out = YieldOverlay.tint(flat, (r, lat, lon) -> lat > 0 ? 1.0 : 0.0, Resource.ICE);
        assertEquals(YieldOverlay.blend(0, 3, Resource.ICE), out.getRGB(5, 1) & 0xFFFFFF);
        assertEquals(YieldOverlay.blend(0, 0, Resource.ICE), out.getRGB(5, 12) & 0xFFFFFF);
        // The rich block touching the poor half is outlined (darker than the rich fill).
        assertTrue((out.getRGB(5, 7) & 0xFF) < (out.getRGB(5, 1) & 0xFF));
    }

    @Test void project_isTheInverseOfUnproject() {
        int size = 500;
        for (int py = 60; py < 440; py += 37) for (int px = 60; px < 440; px += 41) {
            double[] ll = SphereRenderer.unproject(px, py, size, 77.0, 12.0, 1.0);
            if (ll == null) continue;
            double[] p = SphereRenderer.project(ll[0], ll[1], size, 77.0, 12.0, 1.0);
            assertNotNull(p, px + "," + py);
            assertEquals(px, p[0], 1e-6);
            assertEquals(py, p[1], 1e-6);
        }
        // The point straight behind the centre of the globe isn't drawn.
        assertNull(SphereRenderer.project(0, Math.toRadians(77 + 180), size, 77.0, 0.0, 1.0));
    }
}
