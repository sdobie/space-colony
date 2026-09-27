package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GoalProgressTest {
    private static double p(World w, String id) { return GoalCatalog.get(id).displayProgress(w); }

    @Test
    void freshWorld_progressValues() {
        World w = WorldGenerator.generate(1L);          // Earth Hub, pop 100, no ships
        assertEquals(0.10, p(w, "pop-1000"), 1e-9);
        assertEquals(0.01, p(w, "pop-10000"), 1e-9);
        assertEquals(0.0,  p(w, "fleet-10"), 1e-9);
        assertEquals(0.2,  p(w, "five-bodies"), 1e-9);
        assertEquals(0.0,  p(w, "first-mars-colony"), 1e-9);
    }

    @Test
    void achievedGoal_displaysFull_evenIfMeasureDrops() {
        World w = WorldGenerator.generate(1L);
        w.goals.achieved.add("pop-1000");
        assertEquals(1.0, p(w, "pop-1000"), 1e-9);
    }

    @Test
    void progress_isClamped() {
        World w = WorldGenerator.generate(1L);
        w.findSite("site-earth-hub").population = 50_000;
        assertEquals(1.0, p(w, "pop-10000"), 1e-9);
    }

    @Test
    void everyGoal_hasCategory() {
        for (Goal g : GoalCatalog.all()) assertNotNull(g.category(), g.id());
    }
}
