package spacecolony.sim.commands;

public record BuildSiteCommand(
    String siteId,
    String name,
    String bodyId,
    double lat,
    double lon,
    String colonizerShipId
) implements Command {}
