package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTest {
    @Test
    void researchCompletesAfterEnoughTicks() {
        World w = WorldGenerator.generate(1L);
        // Add a research lab to Earth Hub.
        Site s = w.findSite("site-earth-hub");
        s.buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 1000; i++) {
            sim.advance(w);
            if (w.tech.researched.contains("basic-mining")) break;
        }
        assertTrue(w.tech.researched.contains("basic-mining"));
        assertNull(w.tech.activeId);
    }

    @Test
    void noLabs_noProgress() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertFalse(w.tech.researched.contains("basic-mining"));
        assertEquals(0.0, w.tech.accumulatedPoints, 1e-9);
    }
}
