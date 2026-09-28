package spacecolony.sim;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import spacecolony.sim.BuildingSpec.Rate;
import spacecolony.sim.BuildingSpec.Rate.Kind;

/**
 * Building rates and descriptions. The constants are the numbers ProductionPhase used
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
    public static final double REFINERY_ORE = 1.5;
    public static final double REFINERY_METAL_PER_ORE = 0.8;
    public static final double REFINERY_ICE = 1.0;
    public static final double REFINERY_WATER_PER_ICE = 0.9;
    public static final double LAB_POINTS = 1.0;
    public static final int HABITAT_CAP = 100;

    private static final Map<BuildingType, BuildingSpec> SPECS = new EnumMap<>(BuildingType.class);

    static {
        add(BuildingType.HABITAT, "Habitat",
            "Housing. Each level raises the population cap by " + HABITAT_CAP + ".",
            POWER_DRAW, List.of());
        add(BuildingType.FARM, "Farm",
            "Grows food from biomass and water. Without biomass it sits idle.",
            POWER_DRAW, List.of(
                new Rate(Resource.BIOMASS, FARM_BIOMASS, Kind.INPUT),
                new Rate(Resource.WATER, FARM_WATER, Kind.INPUT),
                new Rate(Resource.FOOD, FARM_FOOD, Kind.OUTPUT)));
        add(BuildingType.MINE, "Mine",
            "Digs ore, silicate and ice; output depends on what's in the ground here. At a gas giant,"
                + " with Atmospheric Mining, it also pulls fuel from the air.",
            POWER_DRAW, List.of(
                new Rate(Resource.ORE, MINE_ORE, Kind.YIELD_OUTPUT),
                new Rate(Resource.SILICATE, MINE_SILICATE, Kind.YIELD_OUTPUT),
                new Rate(Resource.ICE, MINE_ICE, Kind.YIELD_OUTPUT)));
        add(BuildingType.REFINERY, "Refinery",
            "Turns ore into metal and ice into water.",
            POWER_DRAW, List.of(
                new Rate(Resource.ORE, REFINERY_ORE, Kind.INPUT),
                new Rate(Resource.METAL, REFINERY_ORE * REFINERY_METAL_PER_ORE, Kind.OUTPUT),
                new Rate(Resource.ICE, REFINERY_ICE, Kind.INPUT),
                new Rate(Resource.WATER, REFINERY_ICE * REFINERY_WATER_PER_ICE, Kind.OUTPUT)));
        add(BuildingType.POWER_PLANT, "Power plant",
            "Solar power for the other buildings. Output falls with distance from the sun; if"
                + " demand outruns it, farms, mines and refineries slow down.",
            0.0, List.of());
        add(BuildingType.SHIPYARD, "Shipyard",
            "Lets this colony build ships.",
            POWER_DRAW, List.of());
        add(BuildingType.RESEARCH_LAB, "Research lab",
            "Produces research points for the active tech.",
            POWER_DRAW, List.of());
    }

    private static void add(BuildingType t, String name, String summary, double draw, List<Rate> rates) {
        SPECS.put(t, new BuildingSpec(t, name, summary, draw, rates));
    }

    public static BuildingSpec get(BuildingType t) { return SPECS.get(t); }

    /** Every spec, in enum order. */
    public static List<BuildingSpec> all() { return List.copyOf(SPECS.values()); }

    public static String displayName(BuildingType t) { return SPECS.get(t).displayName(); }
}
