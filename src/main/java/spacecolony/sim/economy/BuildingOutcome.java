package spacecolony.sim.economy;

import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;

/**
 * How one building did in a day. {@code efficiency} is actual output over wanted output, in
 * [0, 1]. {@code limit} is null when nothing held it back; {@code limitResource} names the
 * input, yield or tech resource for the limits that have one.
 */
public record BuildingOutcome(int index, BuildingType type, int level, boolean enabled,
                              double efficiency, Limit limit, Resource limitResource) {
    /** A limit the player can fix (not just poor ground or a missing tech). */
    public boolean starved() {
        return limit == Limit.NO_INPUT || limit == Limit.SHORT_INPUT;
    }
}
