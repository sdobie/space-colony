package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class DeterminismTest {
    @Test
    void advance_incrementsTick() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        long before = w.tick;
        sim.advance(w);
        assertEquals(before + 1, w.tick);
    }

    @Test
    void advance100_endsAtTick100() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertEquals(100, w.tick);
    }
}
