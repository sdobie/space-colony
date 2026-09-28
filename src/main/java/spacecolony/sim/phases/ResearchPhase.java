package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Site;
import spacecolony.sim.Tech;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.World;

public final class ResearchPhase {
    private ResearchPhase() {}

    public static void run(World w) {
        if (w.tech.activeId == null) return;
        Tech t = TechCatalog.get(w.tech.activeId);
        if (t == null) { w.tech.activeId = null; return; }
        w.tech.accumulatedPoints += pointsPerTick(w);
        if (w.tech.accumulatedPoints >= t.researchCost()) {
            w.tech.researched.add(t.id());
            w.tech.activeId = null;
            w.tech.accumulatedPoints = 0.0;
            w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.RESEARCH_COMPLETED,
                "Researched " + t.name(), null, null, null));
        }
    }

    /** Research points all enabled labs produce per tick. The tech modal shows the same number. */
    public static double pointsPerTick(World w) {
        double points = 0;
        for (Body b : w.bodies) for (Site s : b.sites)
            for (Building bd : s.buildings)
                if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB)
                    points += bd.level * BuildingCatalog.LAB_POINTS * TechEffects.researchLabMultiplier(w.tech);
        return points;
    }
}
