package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrbitTest {
    private static final double EPS = 1e-9;

    @Test
    void positionAtTickZero_isOnXAxisWhenPhaseZero() {
        Orbit o = new Orbit(1.0, 365, 0.0, null);
        double[] p = o.position(0);
        assertEquals(1.0, p[0], EPS);
        assertEquals(0.0, p[1], EPS);
    }

    @Test
    void positionAtQuarterPeriod_is90DegreesAround() {
        Orbit o = new Orbit(1.0, 4, 0.0, null);
        double[] p = o.position(1);
        assertEquals(0.0, p[0], EPS);
        assertEquals(1.0, p[1], EPS);
    }

    @Test
    void positionAtFullPeriod_returnsToStart() {
        Orbit o = new Orbit(2.5, 100, 0.3, null);
        double[] p0 = o.position(0);
        double[] p100 = o.position(100);
        assertEquals(p0[0], p100[0], EPS);
        assertEquals(p0[1], p100[1], EPS);
    }

    @Test
    void phaseOffset_shiftsStartingAngle() {
        Orbit o = new Orbit(1.0, 4, Math.PI / 2, null);
        double[] p = o.position(0);
        assertEquals(0.0, p[0], EPS);
        assertEquals(1.0, p[1], EPS);
    }
}
