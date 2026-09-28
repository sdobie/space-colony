package spacecolony.sim.commands;

import java.util.Map;
import spacecolony.sim.Resource;

/**
 * Send a ship to a site ({@code destSiteId}) or, for a colonizer, to a body with no site
 * ({@code destBodyId}). Exactly one destination is set.
 */
public record DispatchShipCommand(
    String shipId,
    String destSiteId,
    String destBodyId,
    Map<Resource, Double> manifest
) implements Command {
    public DispatchShipCommand {
        if ((destSiteId == null) == (destBodyId == null))
            throw new IllegalArgumentException("Exactly one of destSiteId and destBodyId must be set");
    }

    /** Site-bound trip. */
    public DispatchShipCommand(String shipId, String destSiteId, Map<Resource, Double> manifest) {
        this(shipId, destSiteId, null, manifest);
    }

    /** Colonizer trip to a body that may have no site. */
    public static DispatchShipCommand toBody(String shipId, String bodyId, Map<Resource, Double> manifest) {
        return new DispatchShipCommand(shipId, null, bodyId, manifest);
    }
}
