package spacecolony.sim;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GoalCatalog {
    private static final Map<String, Goal> BY_ID = new LinkedHashMap<>();
    static {
        add(new Goal("first-mars-colony", "Settle Mars",
            "Establish a site on Mars-analog.", 5000, 0,
            w -> w.bodies.stream().filter(b -> b.id.equals("mars")).flatMap(b -> b.sites.stream()).findAny().isPresent()));

        add(new Goal("self-sufficient-mars", "Self-Sufficient Mars",
            "A Mars site with FOOD production rate >= consumption.", 8000, 0,
            w -> w.bodies.stream().filter(b -> b.id.equals("mars")).flatMap(b -> b.sites.stream())
                  .anyMatch(s -> s.productionRateCache.getOrDefault(Resource.FOOD, 0.0) > 0.0 && s.population > 0)));

        add(new Goal("belt-presence", "Reach the Belt",
            "Establish a site on any belt asteroid.", 6000, 200,
            w -> w.bodies.stream().filter(b -> b.type == BodyType.ASTEROID).flatMap(b -> b.sites.stream()).findAny().isPresent()));

        add(new Goal("jovian-presence", "Reach Jovian", "Site on Jovian-analog or its moons.", 10000, 500,
            w -> w.bodies.stream()
                  .filter(b -> b.id.equals("jovian") || (b.orbit.parentBodyId() != null && b.orbit.parentBodyId().equals("jovian")))
                  .flatMap(b -> b.sites.stream()).findAny().isPresent()));

        add(new Goal("pop-1000", "Population: 1,000",
            "Total population across all sites >= 1,000.", 0, 1000,
            w -> w.bodies.stream().flatMap(b -> b.sites.stream()).mapToInt(s -> s.population).sum() >= 1000));

        add(new Goal("pop-10000", "Population: 10,000",
            "Total population across all sites >= 10,000.", 0, 5000,
            w -> w.bodies.stream().flatMap(b -> b.sites.stream()).mapToInt(s -> s.population).sum() >= 10000));

        add(new Goal("fleet-10", "Fleet of 10", "Own at least 10 ships.", 3000, 0,
            w -> w.ships.size() >= 10));

        add(new Goal("five-bodies", "Five-Body Network",
            "Have sites on 5 distinct bodies.", 12000, 1000,
            w -> w.bodies.stream().filter(b -> !b.sites.isEmpty()).count() >= 5));
    }
    private static void add(Goal g) { BY_ID.put(g.id(), g); }
    public static Goal get(String id) { return BY_ID.get(id); }
    public static java.util.Collection<Goal> all() { return BY_ID.values(); }
    private GoalCatalog() {}
}
