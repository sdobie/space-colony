package spacecolony.sim.commands;

/** Removes a finished building and refunds half of what went into it. */
public record DemolishBuildingCommand(String siteId, int buildingId) implements Command {}
