package spacecolony.sim.commands;

public record RepairBuildingCommand(String siteId, int buildingId) implements Command {}
