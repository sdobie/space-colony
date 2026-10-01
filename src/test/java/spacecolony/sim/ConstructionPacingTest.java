package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.UpgradeBuildingCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.sim.BuildingType.*;

/**
 * Balance guard (Plan 8 §8.1): Earth Hub's starting stock pays for an opening (lab, refinery,
 * power plant, then a mine upgrade), and the colony earns the METAL for a factory on its own
 * within 200 days. Pins "the start isn't a dead end", not exact numbers.
 *
 * <p>The factory comes last on purpose: on a metal-poor Earth (seed 42) a factory built first
 * eats every unit of METAL the refinery makes, and the mine upgrade never becomes affordable.
 */
class ConstructionPacingTest {
    static final String HUB = "site-earth-hub";
    static final int DAYS = 200;

    /** The day the factory finished, or -1. */
    static int run(long seed) {
        World w = WorldGenerator.generate(seed);
        w.randomEventsEnabled = false;
        Site hub = w.findSite(HUB);
        Simulator sim = new Simulator();
        for (BuildingType t : new BuildingType[] {RESEARCH_LAB, REFINERY, POWER_PLANT})
            sim.enqueue(new BuildBuildingCommand(HUB, t));
        Building mine = hub.buildings.stream().filter(b -> b.type == MINE).findFirst().orElseThrow();
        boolean factoryOrdered = false;
        for (int day = 1; day <= DAYS; day++) {
            if (mine.level < 2 && !mine.isUnderConstruction()) {
                if (Construction.whyNotUpgrade(hub, mine) == null) sim.enqueue(new UpgradeBuildingCommand(HUB, mine.id));
            } else if (!factoryOrdered && Construction.whyNotBuild(hub, FACTORY) == null) {
                sim.enqueue(new BuildBuildingCommand(HUB, FACTORY));
                factoryOrdered = true;
            }
            sim.advance(w);
            if (hub.buildings.stream().anyMatch(b -> b.type == FACTORY && b.level == 1)) {
                assertEquals(2, mine.level, "seed " + seed);
                for (BuildingType t : new BuildingType[] {RESEARCH_LAB, REFINERY, POWER_PLANT})
                    assertTrue(hub.buildings.stream().anyMatch(b -> b.type == t && b.level == 1), seed + " " + t);
                return day;
            }
        }
        return -1;
    }

    @Test
    void openingFinishesWithin200Days() {
        for (long seed : new long[] {42L, 7777L, 12345L}) {
            int day = run(seed);
            assertTrue(day > 0, "seed " + seed + ": no factory by day " + DAYS);
        }
    }

    public static void main(String[] args) {
        for (long seed : new long[] {42L, 7777L, 12345L}) System.out.println(seed + " -> day " + run(seed));
    }
}
