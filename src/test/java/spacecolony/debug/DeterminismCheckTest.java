package spacecolony.debug;

import org.junit.jupiter.api.Test;
import spacecolony.save.SaveFile;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class DeterminismCheckTest {
    @Test void freshWorld_isDeterministic() throws Exception {
        World w = WorldGenerator.generate(5L);
        DeterminismCheck.Result r = DeterminismCheck.run(w, 1000);
        assertTrue(r.match(), r.firstDiff());
        assertEquals(1000, r.ticks());
        assertEquals(0L, w.tick, "live world must not be advanced");
    }

    @Test void injectedNondeterminism_isDetected() throws Exception {
        World w = WorldGenerator.generate(5L);
        // Perturb only the second restored copy; the check must notice.
        DeterminismCheck.Result r = DeterminismCheck.run(SaveFile.toJson(w), 200,
            (copyIndex, copy) -> { if (copyIndex == 1) copy.credits += 1; });
        assertFalse(r.match());
        assertTrue(r.firstDiff().contains("credits"), r.firstDiff());
    }

    @Test void firstDiff_namesLineAndBothSides() {
        assertEquals("line 2: b ≠ x", DeterminismCheck.firstDiff("a\nb\nc", "a\nx\nc"));
        assertNull(DeterminismCheck.firstDiff("a\nb", "a\nb"));
    }
}
