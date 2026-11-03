package spacecolony.sim;

public enum Resource {
    ORE,
    METAL,
    SILICATE,
    ICE,
    WATER,
    FOOD,
    BIOMASS,
    /** Gas-giant fuel, the best: one unit is one unit of fuel value (Plan 10). */
    HYDROGEN,
    /** Fuel plant fuel, brewed from biomass anywhere; worth half a unit of hydrogen. */
    SYNFUEL,
    COMPONENTS,
    ENERGY;

    /** ENERGY is flow-only; never stockpiled. */
    public boolean isStockpileable() {
        return this != ENERGY;
    }
}
