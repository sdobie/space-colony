package spacecolony.sim;

/** The 8 per-tick phases of {@link Simulator#advance}, in execution order. */
public enum SimPhase { COMMANDS, TICK, ARRIVALS, PRODUCTION, DEPARTURES, EVENTS, RESEARCH, GOALS }
