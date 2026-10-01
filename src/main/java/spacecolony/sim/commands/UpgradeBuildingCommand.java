package spacecolony.sim.commands;

public record UpgradeBuildingCommand(String siteId, int buildingId) implements Command {}
