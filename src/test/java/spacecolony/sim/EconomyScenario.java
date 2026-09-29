package spacecolony.sim;

import java.util.List;
import java.util.Map;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;

/**
 * A busy, deterministic world for economy tests: every building type at Earth Hub, a Mars
 * colony with a refinery, a Jovian mining outpost, and a hauler shuttling between Earth and
 * Mars (including trips that abort for lack of fuel). Random events stay on.
 */
public final class EconomyScenario {
    public static final String HUB = "site-earth-hub";
    public static final String MARS = "site-mars-p";
    public static final String JOVIAN = "site-jovian-p";

    public final World world;
    public final Simulator sim = new Simulator();

    public EconomyScenario(long seed) {
        world = WorldGenerator.generate(seed);
        Site mars = new Site(MARS, "Pavonis", "mars", 0.1, -1.9, 100);
        mars.population = 50;
        mars.buildings.add(new Building(BuildingType.HABITAT, 1));
        mars.buildings.add(new Building(BuildingType.POWER_PLANT, 1));
        mars.buildings.add(new Building(BuildingType.MINE, 1));
        mars.buildings.add(new Building(BuildingType.REFINERY, 1));
        mars.buildings.add(new Building(BuildingType.FARM, 1));
        mars.stockpile.put(Resource.ICE, 300.0);
        mars.stockpile.put(Resource.ORE, 100.0);
        mars.stockpile.put(Resource.FOOD, 60.0);
        mars.stockpile.put(Resource.WATER, 40.0);
        mars.stockpile.put(Resource.FUEL, 900.0);
        world.findBody("mars").sites.add(mars);
        Site jov = new Site(JOVIAN, "Cloudtop", "jovian", 0.3, 0.4, 100);
        jov.buildings.add(new Building(BuildingType.POWER_PLANT, 2));
        jov.buildings.add(new Building(BuildingType.MINE, 2));
        world.findBody("jovian").sites.add(jov);

        // Placed directly (the commands used to apply at tick 1, before any production), so the
        // parity fixture doesn't depend on building costs. Explicit list: new types stay out.
        Site hub = world.findSite(HUB);
        for (BuildingType t : List.of(BuildingType.HABITAT, BuildingType.FARM, BuildingType.MINE,
                BuildingType.REFINERY, BuildingType.POWER_PLANT, BuildingType.SHIPYARD, BuildingType.RESEARCH_LAB))
            hub.addBuilding(new Building(t, 1));
        sim.enqueue(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        sim.enqueue(new QueueResearchCommand("basic-mining"));
    }

    /** Enqueue this tick's scripted commands, then advance one tick. */
    public void step() {
        long t = world.tick;
        if (t == 300 && world.tech.activeId == null) sim.enqueue(new QueueResearchCommand("basic-farming"));
        if (t == 400) world.tech.researched.add("atm-mining");
        Ship h = world.findShip("h1");
        if (h != null && h.state == ShipState.IDLE && h.currentSiteId != null && t % 5 == 0) {
            if (h.currentSiteId.equals(HUB)) {
                sim.enqueue(new DispatchShipCommand("h1", MARS, Map.of(Resource.FOOD, 30.0, Resource.METAL, 20.0)));
            } else {
                sim.enqueue(new DispatchShipCommand("h1", HUB, Map.of(Resource.WATER, 20.0, Resource.METAL, 10.0)));
            }
        }
        sim.advance(world);
    }
}
