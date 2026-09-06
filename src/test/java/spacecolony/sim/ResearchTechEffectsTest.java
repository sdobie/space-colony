package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTechEffectsTest {

    @Test
    void researchI_raisesLabOutputBy25pct() {
        long ticksBase = ticksToComplete("basic-farming");
        long ticksTech = ticksToComplete("basic-farming", "research-i");
        // research-i gives x1.25 -> ticks should drop by ~20%. Allow slack since labs accumulate integer points.
        double ratio = (double) ticksTech / ticksBase;
        assertTrue(ratio < 0.85 && ratio > 0.78,
            "research-i should reduce ticks-to-complete by ~20% (ratio=" + ratio + ")");
    }

    @Test
    void researchIAndII_stackTo15625x() {
        long ticksBase = ticksToComplete("basic-farming");
        long ticksBoth = ticksToComplete("basic-farming", "research-i", "research-ii");
        double ratio = (double) ticksBoth / ticksBase;
        // 1 / (1.25 * 1.25) = 0.64
        assertTrue(ratio < 0.70 && ratio > 0.60,
            "research-i + research-ii should reduce ticks by ~36% (ratio=" + ratio + ")");
    }

    private static long ticksToComplete(String target, String... preResearched) {
        World w = WorldGenerator.generate(1L);
        for (String id : preResearched) w.tech.researched.add(id);
        // Drop a research lab on Earth Hub.
        w.findSite("site-earth-hub").buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand(target));
        long start = w.tick;
        for (int i = 0; i < 10_000; i++) {
            sim.advance(w);
            if (w.tech.researched.contains(target)) return w.tick - start;
        }
        throw new AssertionError("did not finish " + target);
    }
}
