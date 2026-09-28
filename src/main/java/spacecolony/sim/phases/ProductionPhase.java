package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.PopCapBreakdown;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import spacecolony.sim.World;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.FlowLine;
import spacecolony.sim.economy.FlowSource;
import spacecolony.sim.economy.SiteEconomy;

public final class ProductionPhase {
    private ProductionPhase() {}

    public static void run(World w) {
        for (Body b : w.bodies) {
            for (Site s : b.sites) {
                // 0. Population cap from HABITATs + colony-management techs (spec 5.1).
                recomputeCap(s, w.tech);

                // 1-5. Power, eating and building production (SiteEconomy), itemised.
                DayReport day = SiteEconomy.run(w, b, s, s.buildings, s.stockpile);
                for (Resource r : Resource.values())
                    s.productionRateCache.put(r, r.isStockpileable() ? day.productionNet(r) : 0.0);

                // 6. Stockpile clipping.
                for (Resource r : Resource.values()) {
                    if (!r.isStockpileable()) continue;
                    double cap = s.stockpileCap.getOrDefault(r, 1000.0);
                    double cur = s.stockpile.getOrDefault(r, 0.0);
                    if (cur > cap) {
                        s.stockpile.put(r, cap);
                        day.add(new FlowLine(new FlowSource.StorageFull(), r, cap - cur, 0.0));
                    }
                    if (cur < 0) s.stockpile.put(r, 0.0);
                }
                s.lastDay = day;

                // 7. Morale & population updates.
                updateMorale(s, w.tech);
                updatePopulation(s);

                // 8. Recover power plants knocked out by solar flare (Task 23): brownout
                // applies for the tick they were offline, but they come back online for next tick.
                for (Building bd : s.buildings) if (bd.type == BuildingType.POWER_PLANT) bd.enabled = true;
            }
        }
    }

    /** Cap formula lives in {@link PopCapBreakdown} so the detail panel shows the same numbers. */
    private static void recomputeCap(Site s, TechState tech) {
        s.populationCap = PopCapBreakdown.of(s, tech).cap();
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
