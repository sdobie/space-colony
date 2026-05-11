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

    @Test
    void researchReward_survivesQueueResearch() {
        // belt-presence awards 200 research points. Those points should not
        // be wiped out by a subsequent QueueResearchCommand.
        World w = WorldGenerator.generate(1L);
        // Plant a site on an asteroid to trigger belt-presence (reward: 200 research).
        w.findBody("belt-a").sites.add(new Site("site-belt-1", "Belt", "belt-a", 0, 0, 50));
        Simulator sim = new Simulator();
        sim.advance(w);
        assertTrue(w.goals.achieved.contains("belt-presence"));
        double pointsAfterGoal = w.tech.accumulatedPoints;
        assertTrue(pointsAfterGoal >= 200.0, "Goal should have added research points");
        // Now queue research — accumulated points must carry over, not be zeroed.
        // Use ion-drives (cost 300) so the 200-point carryover stays partial rather
        // than instantly completing a cheaper tech.
        sim.enqueue(new spacecolony.sim.commands.QueueResearchCommand("ion-drives"));
        sim.advance(w);
        assertEquals("ion-drives", w.tech.activeId);
        assertTrue(w.tech.accumulatedPoints >= 200.0,
            "Goal-awarded research points must carry across QueueResearchCommand");
    }
}
