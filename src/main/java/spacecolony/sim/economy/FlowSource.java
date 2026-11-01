package spacecolony.sim.economy;

import spacecolony.sim.BuildingType;
import spacecolony.sim.EventKind;

/** Who moved a resource in a colony's day. */
public sealed interface FlowSource {
    /** {@code index} is the building's position in {@code Site.buildings}. */
    record Building(int index, BuildingType type, int level) implements FlowSource {}

    record Population(int people) implements FlowSource {}

    record Shipping(String shipId, String shipName, Kind kind) implements FlowSource {
        public enum Kind { LOADING, UNLOADING, FUEL, RETURNED }
    }

    /** Output thrown away because the stockpile was at its cap. */
    record StorageFull() implements FlowSource {}

    /** Stock destroyed by a random event (Plan 11), e.g. a meteor strike. */
    record EventLoss(EventKind kind) implements FlowSource {}
}
