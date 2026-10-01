package spacecolony.sim;

import java.util.List;

/** What one building type does, for the sim and for the player. See {@link BuildingCatalog}. */
public record BuildingSpec(BuildingType type, String displayName, String summary,
                           double powerDrawPerLevel, List<Rate> rates, BuildCost cost) {
    /** One input or output per level per day, before ground yield, power and tech. */
    public record Rate(Resource resource, double perLevel, Kind kind) {
        public enum Kind {
            INPUT,
            OUTPUT,
            /** Scaled by the ground's yield of the resource at the site. */
            YIELD_OUTPUT
        }
    }
}
