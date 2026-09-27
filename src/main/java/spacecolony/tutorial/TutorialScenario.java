package spacecolony.tutorial;

import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;

/** The prepared world the tutorial runs on (Plan 6 §5.2). */
public final class TutorialScenario {
    /** Fixed so every player sees the same system and the card text matches it. */
    public static final long TUTORIAL_SEED = 20260927L;

    private TutorialScenario() {}

    /**
     * The standard start on a fixed seed, with random events off and enough stock at Earth Hub
     * for a comfortable first colonizer flight (the worst-case Earth→Mars trip with a full hold
     * burns about 200 FUEL).
     */
    public static World world() {
        World w = WorldGenerator.generate(TUTORIAL_SEED);
        w.randomEventsEnabled = false;
        Site hub = w.findSite("site-earth-hub");
        hub.stockpile.put(Resource.FUEL, 400.0);
        hub.stockpile.put(Resource.FOOD, 300.0);
        hub.stockpile.put(Resource.WATER, 300.0);
        return w;
    }
}
