package spacecolony.sim.commands;

/** Turns random events on or off (the tutorial world starts with them off). */
public record SetRandomEventsCommand(boolean enabled) implements Command {}
