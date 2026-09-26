package spacecolony.debug;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTimingsTest {
    @Test
    void simulatorReportsEveryPhaseEachTick() {
        World w = WorldGenerator.generate(3L);
        Simulator sim = new Simulator();
        long[][] seen = new long[1][];
        long[] seenTick = new long[1];
        sim.setPhaseObserver((tick, ns) -> { seen[0] = ns; seenTick[0] = tick; });
        sim.advance(w);
        assertEquals(1, seenTick[0]);
        assertEquals(Simulator.PHASE_NAMES.length, seen[0].length);
        for (long v : seen[0]) assertTrue(v >= 0);
    }

    @Test
    void windowKeepsLast50_andAveragesAndMaxes() {
        PhaseTimings t = new PhaseTimings();
        int n = Simulator.PHASE_NAMES.length;
        for (int i = 1; i <= 60; i++) {
            long[] ns = new long[n];
            ns[0] = i;
            t.onTick(i, ns);
        }
        assertEquals(PhaseTimings.WINDOW, t.sampleCount());
        // Window holds 11..60: mean 35 (integer), max 60.
        assertEquals(35, t.averageNanos()[0]);
        assertEquals(60, t.maxNanos()[0]);
        assertEquals(60, t.lastTotalNanos());
        t.clear();
        assertEquals(0, t.sampleCount());
        assertEquals(0, t.lastTotalNanos());
    }

    @Test
    void observerDoesNotChangeSimulationOutcome() {
        World a = WorldGenerator.generate(99L);
        World b = WorldGenerator.generate(99L);
        Simulator plain = new Simulator();
        Simulator timed = new Simulator();
        timed.setPhaseObserver(new PhaseTimings());
        for (int i = 0; i < 500; i++) { plain.advance(a); timed.advance(b); }
        assertEquals(a.tick, b.tick);
        assertEquals(a.credits, b.credits);
        assertEquals(a.recentEvents.size(), b.recentEvents.size());
        assertEquals(a.findSite("site-earth-hub").population, b.findSite("site-earth-hub").population);
    }

    @Test
    void tickRateMeter_measuresTicksPerSecond() {
        TickRateMeter m = new TickRateMeter();
        assertEquals(0, m.ticksPerSecond(0));
        m.record(0, 10);
        m.record(500_000_000L, 11);
        m.record(1_000_000_000L, 12);
        assertEquals(2.0, m.ticksPerSecond(1_000_000_000L), 1e-9);
        // Everything ages out after the 2 s window.
        assertEquals(0, m.ticksPerSecond(10_000_000_000L));
    }
}
