package spacecolony.sim.commands;

/** Stops a new building or an upgrade under way and refunds its cost in full. */
public record CancelConstructionCommand(String siteId, int buildingId) implements Command {}
