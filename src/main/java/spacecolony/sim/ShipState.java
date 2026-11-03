package spacecolony.sim;

public enum ShipState {
    /** At its shipyard's colony, under construction (Plan 10); can't fly yet. */
    BUILDING,
    IDLE,
    LOADING,
    IN_TRANSIT,
    UNLOADING
}
