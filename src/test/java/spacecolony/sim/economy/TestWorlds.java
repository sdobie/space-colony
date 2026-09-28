package spacecolony.sim.economy;

import java.util.EnumMap;
import java.util.Map;
import spacecolony.sim.Body;
import spacecolony.sim.BodyType;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Orbit;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.World;

/** A one-body, one-site world with flat ground yields, for economy tests. */
final class TestWorlds {
    final World world = new World(1L);
    final Body body;
    final Site site;

    TestWorlds(double au, BodyType type, Map<Resource, Double> yields) {
        Map<Resource, Double> y = new EnumMap<>(Resource.class);
        y.putAll(yields);
        body = new Body("b", "Testia", type, new Orbit(au, 1000, 0.0, null), 1.0, 1.0, 1L,
            (r, lat, lon) -> y.getOrDefault(r, 0.0));
        world.bodies.add(body);
        site = new Site("s", "Test Site", "b", 0.0, 0.0, 100);
        body.sites.add(site);
    }

    static TestWorlds at1Au(Map<Resource, Double> yields) {
        return new TestWorlds(1.0, BodyType.ROCKY, yields);
    }

    TestWorlds with(BuildingType... types) {
        for (BuildingType t : types) site.buildings.add(new Building(t, 1));
        return this;
    }

    TestWorlds stock(Resource r, double v) {
        site.stockpile.put(r, v);
        return this;
    }

    DayReport runOnCopy() {
        return SiteEconomy.run(world, body, site, site.buildings, new EnumMap<>(site.stockpile));
    }
}
