package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import static org.junit.jupiter.api.Assertions.*;

class SphereRendererTest {

    @Test
    void sameInputs_producesIdenticalSphere() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage a = SphereRenderer.render(flat, 200, 30.0, 5.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage b = SphereRenderer.render(flat, 200, 30.0, 5.0, 1.0, 42L, new Color(100, 150, 255));
        assertPixelEqual(a, b);
    }

    @Test
    void differentAtmosphere_producesDifferentSphere() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage blue = SphereRenderer.render(flat, 200, 30.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage red  = SphereRenderer.render(flat, 200, 30.0, 0.0, 1.0, 42L, new Color(255, 100, 100));
        assertFalse(pixelsEqual(blue, red), "Different atmosphere colors should produce different spheres");
    }

    @Test
    void nullAtmosphere_doesNotThrow() {
        BufferedImage flat = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ASTEROID));
        BufferedImage img = SphereRenderer.render(flat, 200, 0.0, 0.0, 1.0, 42L, null);
        assertNotNull(img);
        assertEquals(200, img.getWidth());
        assertEquals(200, img.getHeight());
    }

    @Test
    void rotationChange_movesContent() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage a = SphereRenderer.render(flat, 200, 0.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage b = SphereRenderer.render(flat, 200, 90.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        assertFalse(pixelsEqual(a, b), "90 degrees of rotation should change pixels");
    }

    @Test
    void outputDimensionsMatchSize() {
        BufferedImage flat = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage img = SphereRenderer.render(flat, 300, 0.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        assertEquals(300, img.getWidth());
        assertEquals(300, img.getHeight());
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
