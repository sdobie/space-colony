package spacecolony.sim.commands;

import spacecolony.sim.ShipClass;

public record BuildShipCommand(String shipId, String name, ShipClass shipClass, String shipyardSiteId) implements Command {}
