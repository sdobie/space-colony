package spacecolony.sim.commands;

import java.util.Map;
import spacecolony.sim.Resource;

public record DispatchShipCommand(
    String shipId,
    String destSiteId,
    Map<Resource, Double> manifest
) implements Command {}
