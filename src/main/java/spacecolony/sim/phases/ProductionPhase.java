package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.BodyType;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceYieldSampler;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import spacecolony.sim.World;

public final class ProductionPhase {
    private static final double POP_FOOD_PER_DAY = 0.01;     // per person
    private static final double POP_WATER_PER_DAY = 0.005;

    private ProductionPhase() {}

    public static void run(World w) {
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
                        double output = 10.0 * bd.level / Math.max(0.05, r * r)
                                      * TechEffects.powerPlantMultiplier(w.tech);
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
                                double produced = bd.level * 2.0 * y * powerFactor
                                                * TechEffects.mineOreMultiplier(w.tech);
                                produce(s, Resource.ORE, produced);
                                // Mines also yield silicate, scaled.
                                double si = bd.level * 1.0 * yields.sample(Resource.SILICATE, s.lat, s.lon) * powerFactor
                                          * TechEffects.mineSilicateMultiplier(w.tech);
                                produce(s, Resource.SILICATE, si);
                                // Atmospheric mining: gas-giant MINE buildings extract FUEL when the tech is researched.
                                if (b.type == BodyType.GAS_GIANT && TechEffects.gasGiantFuelEnabled(w.tech)) {
                                    double fy = yields.sample(Resource.FUEL, s.lat, s.lon);
                                    double fuel = bd.level * 2.0 * fy * powerFactor;
                                    produce(s, Resource.FUEL, fuel);
                                }
                            }
                        }
                        case FARM -> {
                            // Consume biomass + water; produce food.
                            double waterDemandFactor = TechEffects.farmWaterDemandMultiplier(w.tech);
                            double biomassConsumed = consume(s, Resource.BIOMASS, bd.level * 0.5 * powerFactor);
                            consume(s, Resource.WATER, bd.level * 0.3 * powerFactor * waterDemandFactor);
                            double foodProduced = bd.level * 1.5 * powerFactor *
                                                  Math.min(1.0, biomassConsumed / Math.max(1e-6, bd.level * 0.5))
                                                * TechEffects.farmFoodMultiplier(w.tech);
                            produce(s, Resource.FOOD, foodProduced);
                        }
                        case REFINERY -> {
                            double mult = TechEffects.refineryMultiplier(w.tech);
                            double oreUsed = consume(s, Resource.ORE, bd.level * 1.5 * powerFactor);
                            produce(s, Resource.METAL, oreUsed * 0.8 * mult);
                            double iceUsed = consume(s, Resource.ICE, bd.level * 1.0 * powerFactor);
                            produce(s, Resource.WATER, iceUsed * 0.9 * mult);
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
                updateMorale(s, w.tech);
                updatePopulation(s);

                // 8. Recover power plants knocked out by solar flare (Task 23): brownout
                // applies for the tick they were offline, but they come back online for next tick.
                for (Building bd : s.buildings) if (bd.type == BuildingType.POWER_PLANT) bd.enabled = true;
            }
        }
    }

    private static double consume(Site s, Resource r, double amount) {
        double have = s.stockpile.getOrDefault(r, 0.0);
        double taken = Math.min(have, amount);
        s.stockpile.put(r, have - taken);
        s.productionRateCache.merge(r, -taken, Double::sum);
        return taken;
    }

    /** Cache reflects gross output before stockpile cap clipping (which happens later). */
    private static void produce(Site s, Resource r, double amount) {
        s.stockpile.merge(r, amount, Double::sum);
        s.productionRateCache.merge(r, amount, Double::sum);
    }

    private static void updateMorale(Site s, TechState tech) {
        boolean shortFood = s.stockpile.getOrDefault(Resource.FOOD, 0.0) < 1e-6;
        boolean shortWater = s.stockpile.getOrDefault(Resource.WATER, 0.0) < 1e-6;
        double ceiling = TechEffects.moraleCeiling(tech);
        if (shortFood || shortWater) s.morale = Math.max(0.0, s.morale - 0.05);
        else s.morale = Math.min(ceiling, s.morale + 0.005);
    }

    private static void updatePopulation(Site s) {
        if (s.morale > 0.7 && s.population < s.populationCap) {
            s.population += Math.max(1, s.population / 200);
        } else if (s.morale < 0.3) {
            s.population = Math.max(0, s.population - Math.max(1, s.population / 100));
        }
    }
}
