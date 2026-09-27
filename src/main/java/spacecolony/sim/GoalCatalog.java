package spacecolony.sim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class GoalCatalog {
    private static final Map<String, Goal> BY_ID = new LinkedHashMap<>();
    static {
        Predicate<World> marsColony = w -> w.bodies.stream().filter(b -> b.id.equals("mars"))
            .flatMap(b -> b.sites.stream()).findAny().isPresent();
        add(new Goal("first-mars-colony", "Settle Mars",
            "Establish a site on Mars-analog.", GoalCategory.EXPANSION, 5000, 0,
            marsColony, binary(marsColony)));

        Predicate<World> selfSufficientMars = w -> w.bodies.stream().filter(b -> b.id.equals("mars")).flatMap(b -> b.sites.stream())
            .anyMatch(s -> s.productionRateCache.getOrDefault(Resource.FOOD, 0.0) > 0.0 && s.population > 0);
        add(new Goal("self-sufficient-mars", "Self-Sufficient Mars",
            "A Mars site with FOOD production rate >= consumption.", GoalCategory.EXPANSION, 8000, 0,
            selfSufficientMars, binary(selfSufficientMars)));

        Predicate<World> belt = w -> w.bodies.stream().filter(b -> b.type == BodyType.ASTEROID)
            .flatMap(b -> b.sites.stream()).findAny().isPresent();
        add(new Goal("belt-presence", "Reach the Belt",
            "Establish a site on any belt asteroid.", GoalCategory.EXPANSION, 6000, 200,
            belt, binary(belt)));

        Predicate<World> jovian = w -> w.bodies.stream()
            .filter(b -> b.id.equals("jovian") || (b.orbit.parentBodyId() != null && b.orbit.parentBodyId().equals("jovian")))
            .flatMap(b -> b.sites.stream()).findAny().isPresent();
        add(new Goal("jovian-presence", "Reach Jovian", "Site on Jovian-analog or its moons.",
            GoalCategory.EXPANSION, 10000, 500, jovian, binary(jovian)));

        add(new Goal("pop-1000", "Population: 1,000",
            "Total population across all sites >= 1,000.", GoalCategory.POPULATION, 0, 1000,
            w -> totalPop(w) >= 1000, w -> totalPop(w) / 1000.0));

        add(new Goal("pop-10000", "Population: 10,000",
            "Total population across all sites >= 10,000.", GoalCategory.POPULATION, 0, 5000,
            w -> totalPop(w) >= 10000, w -> totalPop(w) / 10000.0));

        add(new Goal("fleet-10", "Fleet of 10", "Own at least 10 ships.", GoalCategory.FLEET, 3000, 0,
            w -> w.ships.size() >= 10, w -> w.ships.size() / 10.0));

        add(new Goal("five-bodies", "Five-Body Network",
            "Have sites on 5 distinct bodies.", GoalCategory.EXPANSION, 12000, 1000,
            w -> settledBodies(w) >= 5, w -> settledBodies(w) / 5.0));
    }

    /** Total population across every site. Shared by the pop goals and the goals modal. */
    public static int totalPop(World w) {
        return w.bodies.stream().flatMap(b -> b.sites.stream()).mapToInt(s -> s.population).sum();
    }

    /** Number of bodies with at least one site. */
    public static long settledBodies(World w) {
        return w.bodies.stream().filter(b -> !b.sites.isEmpty()).count();
    }

    private static ToDoubleFunction<World> binary(Predicate<World> p) {
        return w -> p.test(w) ? 1.0 : 0.0;
    }

    private static void add(Goal g) { BY_ID.put(g.id(), g); }
    public static Goal get(String id) { return BY_ID.get(id); }
    public static java.util.Collection<Goal> all() { return BY_ID.values(); }
    private GoalCatalog() {}
}
