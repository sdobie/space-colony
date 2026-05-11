package spacecolony.world;

import spacecolony.sim.*;

public final class WorldGenerator {
    private WorldGenerator() {}

    public static World generate(long seed) {
        World w = new World(seed);
        for (SystemLayout.BodySpec spec : SystemLayout.BODIES) {
            long surfaceSeed = mix(seed, spec.id().hashCode());
            ResourceYieldMap yields = new ResourceYieldMap(surfaceSeed, spec.type());
            Orbit orbit = new Orbit(spec.semiMajorAxis(), spec.period(),
                                    spec.phaseOffset(), spec.parentId());
            Body b = new Body(spec.id(), spec.name(), spec.type(), orbit,
                              spec.mass(), spec.radius(), surfaceSeed, yields);
            w.bodies.add(b);
        }
        // Plant the starting site.
        Body earth = w.findBody(SystemLayout.STARTING_SITE_BODY);
        Site start = new Site("site-earth-hub", "Earth Hub",
                              earth.id, SystemLayout.STARTING_SITE_LAT, SystemLayout.STARTING_SITE_LON, 200);
        start.population = 100;
        start.buildings.add(new Building(BuildingType.HABITAT, 1));
        start.buildings.add(new Building(BuildingType.FARM, 1));
        start.buildings.add(new Building(BuildingType.MINE, 1));
        start.buildings.add(new Building(BuildingType.POWER_PLANT, 1));
        start.buildings.add(new Building(BuildingType.SHIPYARD, 1));
        start.stockpile.put(Resource.FOOD, 200.0);
        start.stockpile.put(Resource.WATER, 200.0);
        start.stockpile.put(Resource.METAL, 100.0);
        start.stockpile.put(Resource.COMPONENTS, 50.0);
        start.stockpile.put(Resource.FUEL, 100.0);
        start.stockpile.put(Resource.BIOMASS, 100.0);
        earth.sites.add(start);
        return w;
    }

    private static long mix(long a, long b) {
        long h = a * 1000003L + b;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}
