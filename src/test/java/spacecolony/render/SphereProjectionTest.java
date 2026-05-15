package spacecolony.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SphereProjectionTest {

    @Test
    void clickOutsideDisc_returnsNull() {
        // (0, 0) corner is well outside the centered disc.
        assertNull(SphereRenderer.unproject(0, 0, 200, 0.0, 0.0, 1.0));
    }

    @Test
    void clickAtCenter_returnsEquatorialOrigin() {
        // Center of the sphere at zero rotation/tilt — nx=0, ny=0, nz=1 → lat=0, lon=0.
        double[] r = SphereRenderer.unproject(100, 100, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertEquals(0.0, r[0], 1e-9, "lat at center should be 0");
        assertEquals(0.0, r[1], 1e-9, "lon at center should be 0");
    }

    @Test
    void rotation_shiftsLongitude() {
        double[] zero = SphereRenderer.unproject(100, 100, 200, 0.0, 0.0, 1.0);
        double[] r90 = SphereRenderer.unproject(100, 100, 200, 90.0, 0.0, 1.0);
        assertEquals(0.0, zero[1], 1e-9);
        assertEquals(Math.toRadians(90.0), r90[1], 1e-9);
    }

    @Test
    void clickAboveCenter_isNorthernHemisphere() {
        // py below center (smaller y in screen coords) maps to positive latitude (north).
        double[] r = SphereRenderer.unproject(100, 60, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertTrue(r[0] > 0, "Click above center should yield northern lat");
    }

    @Test
    void clickBelowCenter_isSouthernHemisphere() {
        double[] r = SphereRenderer.unproject(100, 140, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertTrue(r[0] < 0, "Click below center should yield southern lat");
    }

    @Test
    void unproject_rangeIsBounded() {
        // 1000 evenly-spaced points inside the disc must all produce valid lat/lon.
        int size = 200;
        for (int i = 0; i < 1000; i++) {
            int px = (int) (size * (0.1 + 0.8 * (i % 32) / 32.0));
            int py = (int) (size * (0.1 + 0.8 * ((i / 32) % 32) / 32.0));
            double[] r = SphereRenderer.unproject(px, py, size, 0.0, 0.0, 1.0);
            if (r == null) continue;
            assertTrue(r[0] >= -Math.PI / 2 - 1e-9 && r[0] <= Math.PI / 2 + 1e-9, "lat out of range: " + r[0]);
            assertTrue(r[1] >= -Math.PI - 1e-9 && r[1] <= Math.PI + 1e-9, "lon out of range: " + r[1]);
        }
    }
}
