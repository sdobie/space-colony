package spacecolony.sim.commands;

import spacecolony.sim.BuildingType;

public record BuildBuildingCommand(String siteId, BuildingType type) implements Command {}
