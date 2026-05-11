package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import spacecolony.sim.commands.*;
import java.util.Map;
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

    @Test
    void fullEndToEnd_determinism() {
        World a = spacecolony.world.WorldGenerator.generate(7777L);
        World b = spacecolony.world.WorldGenerator.generate(7777L);
        Simulator s1 = new Simulator();
        Simulator s2 = new Simulator();
        // Same command stream into both sims at same ticks.
        s1.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        s2.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        s1.enqueue(new QueueResearchCommand("basic-mining"));
        s2.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 1000; i++) { s1.advance(a); s2.advance(b); }
        // Final state should match.
        assertEquals(a.tick, b.tick);
        assertEquals(a.credits, b.credits);
        assertEquals(a.tech.researched, b.tech.researched);
        assertEquals(a.goals.achieved, b.goals.achieved);
        Site sa = a.findSite("site-earth-hub");
        Site sb = b.findSite("site-earth-hub");
        assertEquals(sa.population, sb.population);
        assertEquals(sa.morale, sb.morale, 1e-9);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-6, "stockpile " + r);
        }
    }
}
