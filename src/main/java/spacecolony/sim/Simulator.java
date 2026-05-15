package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.Command;

/**
 * Pure simulation engine. Advances {@link World} by one tick at a time. Player intent
 * enters via the command queue; UI never mutates World directly.
 *
 * Per-tick phases (run in this order):
 *   1. Drain command queue
 *   2. Advance tick + orbits (positions are computed from tick on demand)
 *   3. Advance ships in transit (arrivals)
 *   4. Per-site production & consumption
 *   5. Loading / unloading ships (departures)
 *   6. Random events
 *   7. Research progress
 *   8. Goal check
 */
public class Simulator {
    private final Deque<Command> commandQueue = new ArrayDeque<>();

    public void enqueue(Command c) { commandQueue.addLast(c); }

    public void advance(World w) {
        drainCommands(w);
        w.tick++;
        advanceTransits(w);
        productionAndConsumption(w);
        loadingAndUnloading(w);
        randomEvents(w);
        researchProgress(w);
        goalCheck(w);
    }

    private void drainCommands(World w) {
        spacecolony.sim.phases.CommandPhase.drain(w, commandQueue);
    }

    private void advanceTransits(World w) {
        spacecolony.sim.phases.TransitPhase.advanceTransits(w);
    }

    private void loadingAndUnloading(World w) {
        spacecolony.sim.phases.TransitPhase.loadingAndUnloading(w);
    }

    private void productionAndConsumption(World w) {
        spacecolony.sim.phases.ProductionPhase.run(w);
    }

    private static final double EVENT_BASE_RATE = 0.0008; // per body per tick

    private void randomEvents(World w) {
        java.util.Random rng = DeterministicRng.forStep(w.seed, w.tick, 6L);
        EventKind[] kinds = {EventKind.METEOR_STRIKE, EventKind.SOLAR_FLARE,
                              EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK};
        for (Body b : w.bodies) {
            if (rng.nextDouble() < EVENT_BASE_RATE) {
                EventKind k = kinds[rng.nextInt(kinds.length)];
                applyEvent(w, b, k, rng);
            }
        }
    }

    private void applyEvent(World w, Body b, EventKind k, java.util.Random rng) {
        switch (k) {
            case METEOR_STRIKE -> {
                for (Site s : b.sites) {
                    if (s.buildings.isEmpty()) continue;
                    int idx = rng.nextInt(s.buildings.size());
                    s.buildings.get(idx).enabled = false;
                }
                w.emit(new Event(w.tick, EventSeverity.WARNING, k,
                    "Meteor strike on " + b.name, b.id, null, null));
            }
            case SOLAR_FLARE -> {
                // Disable all power plants for 1 tick (re-enabled at end of next productionAndConsumption).
                for (Body bb : w.bodies) for (Site s : bb.sites)
                    for (Building bd : s.buildings) if (bd.type == BuildingType.POWER_PLANT) bd.enabled = false;
                w.emit(new Event(w.tick, EventSeverity.WARNING, k,
                    "Solar flare disrupted system-wide power", null, null, null));
            }
            case EQUIPMENT_FAILURE -> {
                for (Site s : b.sites) {
                    for (Building bd : s.buildings) {
                        if (bd.enabled && rng.nextDouble() < 0.3) { bd.enabled = false; break; }
                    }
                }
                w.emit(new Event(w.tick, EventSeverity.WARNING, k,
                    "Equipment failure on " + b.name, b.id, null, null));
            }
            case DISEASE_OUTBREAK -> {
                for (Site s : b.sites) if (s.population > 0) {
                    int loss = Math.max(1, s.population / 10);
                    s.population -= loss;
                    s.morale = Math.max(0, s.morale - 0.2);
                }
                w.emit(new Event(w.tick, EventSeverity.WARNING, k,
                    "Disease outbreak on " + b.name, b.id, null, null));
            }
            default -> {}
        }
    }

    private void researchProgress(World w) {
        if (w.tech.activeId == null) return;
        Tech t = TechCatalog.get(w.tech.activeId);
        if (t == null) { w.tech.activeId = null; return; }
        double points = 0;
        for (Body b : w.bodies) for (Site s : b.sites)
            for (Building bd : s.buildings)
                if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB) points += bd.level * 1.0;
        w.tech.accumulatedPoints += points;
        if (w.tech.accumulatedPoints >= t.researchCost()) {
            w.tech.researched.add(t.id());
            w.tech.activeId = null;
            w.tech.accumulatedPoints = 0.0;
            w.emit(new Event(w.tick, EventSeverity.INFO, EventKind.RESEARCH_COMPLETED,
                "Researched " + t.name(), null, null, null));
        }
    }
    private void goalCheck(World w) {
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
