package spacecolony.sim;

public enum Resource {
    ORE,
    METAL,
    SILICATE,
    ICE,
    WATER,
    FOOD,
    BIOMASS,
    FUEL,
    COMPONENTS,
    ENERGY;

    /** ENERGY is flow-only; never stockpiled. */
    public boolean isStockpileable() {
        return this != ENERGY;
    }
}
