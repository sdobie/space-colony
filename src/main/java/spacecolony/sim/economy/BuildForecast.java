package spacecolony.sim.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingSpec;
import spacecolony.sim.BuildingType;
import spacecolony.sim.PopCapBreakdown;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.World;

/**
 * What adding one L1 building of {@code type} to a colony would do tomorrow: the colony's
 * day with and without it, from today's stock, plus plain-language facts and warnings.
 * Never touches the world.
 */
public record BuildForecast(BuildingType type, DayReport before, DayReport after,
                            List<String> facts, List<String> warnings) {

    public static BuildForecast of(World w, Site s, BuildingType type) {
        Body b = w.findBody(s.bodyId);
        DayReport before = SiteEconomy.run(w, b, s, s.buildings, new EnumMap<>(s.stockpile));
        // Same Building objects, so today's disabled buildings stay disabled; the new one
        // goes at the end, where BuildBuildingCommand puts it.
        List<Building> plus = new ArrayList<>(s.buildings);
        plus.add(new Building(type, 1));
        DayReport after = SiteEconomy.run(w, b, s, plus, new EnumMap<>(s.stockpile));
        BuildingOutcome added = after.outcome(plus.size() - 1);
        return new BuildForecast(type, before, after,
            facts(w, s, type, before, after), warnings(s, type, before, after, added));
    }

    /** The next day at this colony as it stands; shown when {@code Site.lastDay} is null. */
    public static DayReport estimate(World w, Site s) {
        DayReport d = SiteEconomy.run(w, w.findBody(s.bodyId), s, s.buildings, new EnumMap<>(s.stockpile));
        d.estimate = true;
        return d;
    }

    /** Change in {@code r}'s net per day. */
    public double delta(Resource r) {
        return after.net(r) - before.net(r);
    }

    private static List<String> facts(World w, Site s, BuildingType type, DayReport before, DayReport after) {
        List<String> out = new ArrayList<>();
        switch (type) {
            case HABITAT -> {
                PopCapBreakdown cap = PopCapBreakdown.of(s, w.tech);
                int next = (int) Math.round((cap.siteBase() + cap.habitatBoost() + BuildingCatalog.HABITAT_CAP)
                                            * cap.techMultiplier());
                out.add("Population cap " + cap.cap() + " → " + next);
            }
            case RESEARCH_LAB -> {
                double lab = BuildingCatalog.LAB_POINTS * TechEffects.researchLabMultiplier(w.tech);
                double now = researchPerDay(w);
                out.add(String.format("Research %.1f → %.1f points a day", now, now + lab));
            }
            case SHIPYARD -> {
                boolean has = s.buildings.stream().anyMatch(x -> x.type == BuildingType.SHIPYARD && x.enabled);
                out.add(has ? s.name + " already has a shipyard; a second one adds nothing yet."
                            : "Lets " + s.name + " build ships.");
            }
            case POWER_PLANT -> out.add(String.format("Power %.1f → %.1f made (%.1f used)",
                before.powerMade, after.powerMade, after.powerUsed));
            default -> {}
        }
        return out;
    }

    private static List<String> warnings(Site s, BuildingType type, DayReport before, DayReport after,
                                         BuildingOutcome added) {
        List<String> out = new ArrayList<>();
        BuildingSpec spec = BuildingCatalog.get(type);
        // Ground.
        if (added.limit() == Limit.NO_YIELD) {
            out.add("No " + added.limitResource() + " in the ground here, so it would dig almost nothing.");
        } else if (added.limit() == Limit.LOW_YIELD) {
            out.add("Poor " + added.limitResource() + " here, so output is low.");
        }
        // Inputs.
        List<Resource> warned = new ArrayList<>();
        for (BuildingSpec.Rate rate : spec.rates()) {
            if (rate.kind() != BuildingSpec.Rate.Kind.INPUT) continue;
            Resource r = rate.resource();
            double stock = s.stockpile.getOrDefault(r, 0.0);
            double net = after.net(r);
            boolean madeHere = makes(before, r);
            if (stock <= 1e-6 && !madeHere) {
                out.add("Needs " + r + ". There's none here and nothing here makes it.");
                warned.add(r);
            } else if (net < -Outlook.STEADY) {
                String days = switch (Outlook.of(stock, s.stockpileCap.getOrDefault(r, 1000.0), net)) {
                    case Outlook.EmptyIn e -> "lasts about " + e.days() + " days";
                    default -> "runs out soon";
                };
                out.add("Needs " + r + ". " + (madeHere ? "This colony uses more than it makes; " : "Nothing here makes it; ")
                    + String.format("the %.0f in stock %s.", stock, days));
                warned.add(r);
            }
        }
        // Tech.
        if (added.limit() == Limit.NEEDS_TECH)
            out.add("Research Atmospheric Mining to pull fuel from this atmosphere.");
        // Power.
        if (after.powerFactor < 1.0 - 1e-9) {
            if (before.powerFactor < 1.0 - 1e-9) {
                out.add(String.format("Makes the brownout worse: %.0f%% → %.0f%%.",
                    before.powerFactor * 100, after.powerFactor * 100));
            } else {
                out.add(String.format("Power short: %.1f made, %.1f needed. Farms, mines and refineries here run at %.0f%%.",
                    after.powerMade, after.powerUsed, after.powerFactor * 100));
            }
        }
        // Knock-on: resources that go from steady or growing to shrinking.
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable() || warned.contains(r)) continue;
            double was = before.net(r), now = after.net(r);
            if (was >= -Outlook.STEADY && now < -Outlook.STEADY)
                out.add(String.format("%s goes from %+.1f to %+.1f a day.", r, was, now));
        }
        return out;
    }

    /** True when a building here produced {@code r} (shipping doesn't count). */
    private static boolean makes(DayReport d, Resource r) {
        for (FlowLine l : d.lines())
            if (l.resource() == r && l.source() instanceof FlowSource.Building && l.amount() > 1e-9) return true;
        return false;
    }

    /** Same sum as ResearchPhase.pointsPerTick (sim.economy can't import sim.phases). */
    private static double researchPerDay(World w) {
        double points = 0;
        for (Body b : w.bodies) for (Site s : b.sites)
            for (Building bd : s.buildings)
                if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB)
                    points += bd.level * BuildingCatalog.LAB_POINTS * TechEffects.researchLabMultiplier(w.tech);
        return points;
    }
}
