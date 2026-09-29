package spacecolony.sim.economy;

/** Why a building ran below its full rate yesterday, most important first. */
public enum Limit {
    /** Damaged by an event, until repaired. */
    DISABLED,
    /** Got none of an input it needs. */
    NO_INPUT,
    /** Got some, but under 99%, of an input it needs. */
    SHORT_INPUT,
    /** Power demand outran supply. */
    BROWNOUT,
    /** Could make more with a tech (gas-giant fuel without Atmospheric Mining). */
    NEEDS_TECH,
    /** The ground here has (almost) none of the resource. */
    NO_YIELD,
    /** The ground here is poor in the resource. */
    LOW_YIELD,
    /** A new building that isn't finished yet. */
    CONSTRUCTING
}
