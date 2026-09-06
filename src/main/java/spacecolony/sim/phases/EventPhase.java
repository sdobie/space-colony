package spacecolony.sim.phases;

import java.util.Random;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.DeterministicRng;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.World;

public final class EventPhase {
    private static final double EVENT_BASE_RATE = 0.0008; // per body per tick
    private static final EventKind[] RANDOM_KINDS = {
        EventKind.METEOR_STRIKE, EventKind.SOLAR_FLARE,
        EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK
    };

    private EventPhase() {}

    public static void run(World w) {
        Random rng = DeterministicRng.forStep(w.seed, w.tick, 6L);
        for (Body b : w.bodies) {
            if (rng.nextDouble() < EVENT_BASE_RATE) {
                EventKind k = RANDOM_KINDS[rng.nextInt(RANDOM_KINDS.length)];
                applyEvent(w, b, k, rng);
            }
        }
    }

    /**
     * Test seam: apply a specific event kind to a body, bypassing the random draw in
     * {@link #run}. Public because tests live in {@code spacecolony.sim}, not this package.
     */
    public static void applyForTest(World w, Body b, EventKind kind, Random rng) {
        applyEvent(w, b, kind, rng);
    }

    private static void applyEvent(World w, Body b, EventKind k, Random rng) {
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
                // Truncating (not rounding) keeps severity=1.0 identical to pre-tech behaviour.
                double severity = TechEffects.diseaseSeverityMultiplier(w.tech);
                for (Site s : b.sites) if (s.population > 0) {
                    int loss = (int) Math.max(1, (s.population / 10.0) * severity);
                    s.population -= loss;
                    s.morale = Math.max(0, s.morale - 0.2 * severity);
                }
                w.emit(new Event(w.tick, EventSeverity.WARNING, k,
                    "Disease outbreak on " + b.name, b.id, null, null));
            }
            default -> {}
        }
    }
}
