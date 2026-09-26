package spacecolony.debug;

import org.junit.jupiter.api.Test;
import spacecolony.sim.SimPhase;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTimingsTest {
    @Test void keepsLast50PerPhase_meanLeMax() {
        PhaseTimings pt = new PhaseTimings(50);
        for (long t = 1; t <= 60; t++) for (SimPhase p : SimPhase.values()) pt.phaseDone(t, p, t * 1000);
        for (SimPhase p : SimPhase.values()) {
            PhaseTimings.Stat s = pt.stat(p);
            assertEquals(50, s.samples());
            assertEquals(60_000, s.maxNanos());
            assertEquals((11 + 60) / 2.0 * 1000, s.meanNanos(), 1e-6);
        }
    }

    @Test void empty_isZero() {
        PhaseTimings.Stat s = new PhaseTimings(50).stat(SimPhase.EVENTS);
        assertEquals(0, s.samples());
        assertEquals(0, s.maxNanos());
    }
}
