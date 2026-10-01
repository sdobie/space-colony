package spacecolony.render;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Resource;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 9 §5: the overlay tints by yield, in the renderer's map coordinates. */
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

    @Test void richGroundIsBrightYellow_poorGroundIsDark() {
        BufferedImage flat = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) flat.setRGB(x, y, 0xC0603A); // Mars-ish
        BufferedImage none = YieldOverlay.tint(flat, (r, lat, lon) -> 0.0, Resource.ORE);
        BufferedImage rich = YieldOverlay.tint(flat, (r, lat, lon) -> 1.0, Resource.ORE);
        assertEquals(8, rich.getWidth());
        assertEquals(4, rich.getHeight());
        int c = rich.getRGB(3, 2), n = none.getRGB(3, 2);
        assertTrue(((c >> 16) & 0xFF) > 0xC0 && ((c >> 8) & 0xFF) > 0xB0 && (c & 0xFF) < 0x60, Integer.toHexString(c));
        assertTrue(((n >> 16) & 0xFF) < 0x60 && ((n >> 8) & 0xFF) < 0x60, Integer.toHexString(n));
    }

    @Test void tint_followsTheSampler() {
        BufferedImage flat = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        // Yield 1 in the northern hemisphere only.
        BufferedImage out = YieldOverlay.tint(flat, (r, lat, lon) -> lat > 0 ? 1.0 : 0.0, Resource.ICE);
        assertEquals(YieldOverlay.blend(0, 1.0), out.getRGB(5, 1) & 0xFFFFFF);
        assertEquals(YieldOverlay.blend(0, 0.0), out.getRGB(5, 6) & 0xFFFFFF);
    }
}
