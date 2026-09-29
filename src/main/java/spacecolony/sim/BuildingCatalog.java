package spacecolony.sim;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import spacecolony.sim.BuildingSpec.Rate;
import spacecolony.sim.BuildingSpec.Rate.Kind;

/**
 * Building rates, costs and descriptions. The constants are the numbers ProductionPhase used
 * through Plan 6; ProductionParityTest pins them.
 */
public final class BuildingCatalog {
    private BuildingCatalog() {}

    /** Energy every enabled non-power building draws per level per day. */
    public static final double POWER_DRAW = 2.0;
    /** Power plant output per level at 1 AU; falls with distance² from the sun. */
    public static final double POWER_PLANT_OUTPUT = 10.0;
    public static final double MINE_ORE = 2.0;
    public static final double MINE_SILICATE = 1.0;
    /** Ice dug where the ground holds it (richest on icy bodies); refineries turn it into water. */
    public static final double MINE_ICE = 2.0;
    /** Gas-giant atmospheric fuel, once Atmospheric Mining is researched. */
    public static final double MINE_GAS_FUEL = 2.0;
    public static final double FARM_BIOMASS = 0.5;
    public static final double FARM_WATER = 0.3;
    public static final double FARM_FOOD = 1.5;
    /** Seed stock a farm saves from each harvest, anywhere (spec: biomass "closes loop on repeat planting"). */
    public static final double FARM_BIOMASS_RESEED = 0.3;
    /** Extra biomass grown from living soil, × the ground's BIOMASS yield (Earth-like bodies have it). */
    public static final double FARM_SOIL_BIOMASS = 2.5;
    public static final double REFINERY_ORE = 1.5;
    public static final double REFINERY_METAL_PER_ORE = 0.8;
    public static final double REFINERY_ICE = 1.0;
    public static final double REFINERY_WATER_PER_ICE = 0.9;
    public static final double LAB_POINTS = 1.0;
    public static final int HABITAT_CAP = 100;
    /** Factory: metal and silicate in, components out, per level per day. */
    public static final double FACTORY_METAL = 0.5;
    public static final double FACTORY_SILICATE = 0.5;
    public static final double FACTORY_COMPONENTS = 0.5;

    private static final Map<BuildingType, BuildingSpec> SPECS = new EnumMap<>(BuildingType.class);

    static {
        add(BuildingType.HABITAT, "Habitat",
            "Housing. Each level raises the population cap by " + HABITAT_CAP + ".",
            POWER_DRAW, List.of(),
            BuildCost.of(25, 5, 5));
        add(BuildingType.FARM, "Farm",
            "Grows food from biomass and water, and saves biomass to replant. On living soil it"
                + " grows more biomass than it plants; elsewhere it needs biomass shipped in, and"
                + " without any it sits idle.",
            POWER_DRAW, List.of(
                new Rate(Resource.BIOMASS, FARM_BIOMASS, Kind.INPUT),
                new Rate(Resource.WATER, FARM_WATER, Kind.INPUT),
                new Rate(Resource.FOOD, FARM_FOOD, Kind.OUTPUT),
                new Rate(Resource.BIOMASS, FARM_BIOMASS_RESEED, Kind.OUTPUT),
                new Rate(Resource.BIOMASS, FARM_SOIL_BIOMASS, Kind.YIELD_OUTPUT)),
            BuildCost.of(15, 5, 3));
        add(BuildingType.MINE, "Mine",
            "Digs ore, silicate and ice; output depends on what's in the ground here. At a gas giant,"
                + " with Atmospheric Mining, it also pulls fuel from the air.",
            POWER_DRAW, List.of(
                new Rate(Resource.ORE, MINE_ORE, Kind.YIELD_OUTPUT),
                new Rate(Resource.SILICATE, MINE_SILICATE, Kind.YIELD_OUTPUT),
                new Rate(Resource.ICE, MINE_ICE, Kind.YIELD_OUTPUT)),
            BuildCost.of(20, 5, 4));
        add(BuildingType.REFINERY, "Refinery",
            "Turns ore into metal and ice into water.",
            POWER_DRAW, List.of(
                new Rate(Resource.ORE, REFINERY_ORE, Kind.INPUT),
                new Rate(Resource.METAL, REFINERY_ORE * REFINERY_METAL_PER_ORE, Kind.OUTPUT),
                new Rate(Resource.ICE, REFINERY_ICE, Kind.INPUT),
                new Rate(Resource.WATER, REFINERY_ICE * REFINERY_WATER_PER_ICE, Kind.OUTPUT)),
            BuildCost.of(25, 10, 5));
        add(BuildingType.FACTORY, "Factory",
            "Assembles components from metal and silicate. Components pay for new buildings and"
                + " upgrades.",
            POWER_DRAW, List.of(
                new Rate(Resource.METAL, FACTORY_METAL, Kind.INPUT),
                new Rate(Resource.SILICATE, FACTORY_SILICATE, Kind.INPUT),
                new Rate(Resource.COMPONENTS, FACTORY_COMPONENTS, Kind.OUTPUT)),
            BuildCost.of(30, 5, 5));
        add(BuildingType.POWER_PLANT, "Power plant",
            "Solar power for the other buildings. Output falls with distance from the sun; if"
                + " demand outruns it, farms, mines, refineries and factories slow down.",
            0.0, List.of(),
            BuildCost.of(20, 10, 4));
        add(BuildingType.SHIPYARD, "Shipyard",
            "Lets this colony build ships.",
            POWER_DRAW, List.of(),
            BuildCost.of(50, 20, 10));
        add(BuildingType.RESEARCH_LAB, "Research lab",
            "Produces research points for the active tech.",
            POWER_DRAW, List.of(),
            BuildCost.of(20, 10, 5));
    }

    private static void add(BuildingType t, String name, String summary, double draw, List<Rate> rates,
                            BuildCost cost) {
        SPECS.put(t, new BuildingSpec(t, name, summary, draw, rates, cost));
    }

    public static BuildingSpec get(BuildingType t) { return SPECS.get(t); }

    /** Every spec, in enum order. */
    public static List<BuildingSpec> all() { return List.copyOf(SPECS.values()); }

    public static String displayName(BuildingType t) { return SPECS.get(t).displayName(); }
}
