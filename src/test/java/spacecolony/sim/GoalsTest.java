package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GoalsTest {
    @Test
    void firstMarsColonyGoal_firesWhenSitePlacedOnMars() {
        World w = WorldGenerator.generate(1L);
        Body mars = w.findBody("mars");
        Site newSite = new Site("site-mars-1", "Mars Alpha", "mars", 0.0, 0.0, 50);
        mars.sites.add(newSite);
        long beforeCredits = w.credits;
        new Simulator().advance(w);
        assertTrue(w.goals.achieved.contains("first-mars-colony"));
        assertTrue(w.credits > beforeCredits, "Credits should be awarded for goal");
    }

    @Test
    void goalsDoNotEndGame_simStillRuns() {
        World w = WorldGenerator.generate(1L);
        Body mars = w.findBody("mars");
        mars.sites.add(new Site("site-mars-1", "Mars", "mars", 0, 0, 50));
        Simulator sim = new Simulator();
        sim.advance(w);
        sim.advance(w);
        sim.advance(w);
        assertEquals(3, w.tick);
    }
}
