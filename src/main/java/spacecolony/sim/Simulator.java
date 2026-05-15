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

    private static final double POP_FOOD_PER_DAY = 0.01;     // per person
    private static final double POP_WATER_PER_DAY = 0.005;

    private void productionAndConsumption(World w) {
        for (Body b : w.bodies) {
            for (Site s : b.sites) {
                // 1. Power balance.
                double powerProduced = 0;
                double powerDemand = 0;
                for (Building bd : s.buildings) {
                    if (!bd.enabled) continue;
                    if (bd.type == BuildingType.POWER_PLANT) {
                        // Solar output scales with 1/r^2 (r = distance from sun, in AU).
                        double r = OrbitalGeometry.sunDistance(w, b);
                        double output = 10.0 * bd.level / Math.max(0.05, r * r);
                        powerProduced += output;
                    } else {
                        powerDemand += 2.0 * bd.level;
                    }
                }
                double powerFactor = powerDemand <= 0 ? 1.0 : Math.min(1.0, powerProduced / powerDemand);

                // 2. Reset cache; populate with deltas.
                for (Resource r : Resource.values()) s.productionRateCache.put(r, 0.0);

                // 3. Consumption (always paid first).
                double foodNeed = s.population * POP_FOOD_PER_DAY;
                double waterNeed = s.population * POP_WATER_PER_DAY;
                consume(s, Resource.FOOD, foodNeed);
                consume(s, Resource.WATER, waterNeed);

                // 4. Sample yields via the sim-level ResourceYieldSampler interface; world supplies the implementation.
                ResourceYieldSampler yields = b.resourceYields;

                // 5. Production by building type.
                for (Building bd : s.buildings) {
                    if (!bd.enabled) continue;
                    switch (bd.type) {
                        case MINE -> {
                            if (yields != null) {
                                double y = yields.sample(Resource.ORE, s.lat, s.lon);
                                double produced = bd.level * 2.0 * y * powerFactor;
                                produce(s, Resource.ORE, produced);
                                // Mines also yield silicate, scaled.
                                double si = bd.level * 1.0 * yields.sample(Resource.SILICATE, s.lat, s.lon) * powerFactor;
                                produce(s, Resource.SILICATE, si);
                            }
                        }
                        case FARM -> {
                            // Consume biomass + water; produce food.
                            double biomassConsumed = consume(s, Resource.BIOMASS, bd.level * 0.5 * powerFactor);
                            consume(s, Resource.WATER, bd.level * 0.3 * powerFactor);
                            double foodProduced = bd.level * 1.5 * powerFactor *
                                                  Math.min(1.0, biomassConsumed / Math.max(1e-6, bd.level * 0.5));
                            produce(s, Resource.FOOD, foodProduced);
                        }
                        case REFINERY -> {
                            double oreUsed = consume(s, Resource.ORE, bd.level * 1.5 * powerFactor);
                            produce(s, Resource.METAL, oreUsed * 0.8);
                            double iceUsed = consume(s, Resource.ICE, bd.level * 1.0 * powerFactor);
                            produce(s, Resource.WATER, iceUsed * 0.9);
                        }
                        case POWER_PLANT, HABITAT, SHIPYARD, RESEARCH_LAB -> { /* tracked elsewhere */ }
                    }
                }

                // 6. Stockpile clipping.
                for (Resource r : Resource.values()) {
                    if (!r.isStockpileable()) continue;
                    double cap = s.stockpileCap.getOrDefault(r, 1000.0);
                    double cur = s.stockpile.getOrDefault(r, 0.0);
                    if (cur > cap) s.stockpile.put(r, cap);
                    if (cur < 0) s.stockpile.put(r, 0.0);
                }

                // 7. Morale & population updates.
                updateMorale(s);
                updatePopulation(s);

                // 8. Recover power plants knocked out by solar flare (Task 23): brownout
                // applies for the tick they were offline, but they come back online for next tick.
                for (Building bd : s.buildings) if (bd.type == BuildingType.POWER_PLANT) bd.enabled = true;
            }
        }
    }

    private double consume(Site s, Resource r, double amount) {
        double have = s.stockpile.getOrDefault(r, 0.0);
        double taken = Math.min(have, amount);
        s.stockpile.put(r, have - taken);
        s.productionRateCache.merge(r, -taken, Double::sum);
        return taken;
    }

    /** Cache reflects gross output before stockpile cap clipping (which happens later). */
    private void produce(Site s, Resource r, double amount) {
        s.stockpile.merge(r, amount, Double::sum);
        s.productionRateCache.merge(r, amount, Double::sum);
    }

    private void updateMorale(Site s) {
        boolean shortFood = s.stockpile.getOrDefault(Resource.FOOD, 0.0) < 1e-6;
        boolean shortWater = s.stockpile.getOrDefault(Resource.WATER, 0.0) < 1e-6;
        if (shortFood || shortWater) s.morale = Math.max(0.0, s.morale - 0.05);
        else s.morale = Math.min(1.0, s.morale + 0.005);
    }

    private void updatePopulation(Site s) {
        if (s.morale > 0.7 && s.population < s.populationCap) {
            s.population += Math.max(1, s.population / 200);
        } else if (s.morale < 0.3) {
            s.population = Math.max(0, s.population - Math.max(1, s.population / 100));
        }
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
