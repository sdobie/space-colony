package spacecolony.sim;

public class Building {
    public final BuildingType type;
    /** 0 while a new building is under construction. */
    public int level;
    /** False when damaged by an event (e.g. equipment failure) until repaired. */
    public boolean enabled;
    /** Unique within its site; 0 until {@link Site#addBuilding} assigns one. */
    public int id;
    /** Days until the current construction step (new building or upgrade) finishes; 0 when idle. */
    public int daysLeft;

    public Building(BuildingType type, int level) {
        this.type = type;
        this.level = level;
        this.enabled = true;
    }

    /** Built and not damaged: does its job today. */
    public boolean isOperational() { return enabled && level > 0; }

    public boolean isUnderConstruction() { return daysLeft > 0; }
}
