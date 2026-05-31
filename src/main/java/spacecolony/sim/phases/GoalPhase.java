package spacecolony.sim.phases;

import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Goal;
import spacecolony.sim.GoalCatalog;
import spacecolony.sim.World;

public final class GoalPhase {
    private GoalPhase() {}

    public static void run(World w) {
        for (Goal g : GoalCatalog.all()) {
            if (w.goals.achieved.contains(g.id())) continue;
            if (g.predicate().test(w)) {
                w.goals.achieved.add(g.id());
                w.credits += g.creditReward();
                w.tech.accumulatedPoints += g.researchReward();
                w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.GOAL_ACHIEVED,
                    "Goal achieved: " + g.name(), null, null, null));
            }
        }
    }
}
