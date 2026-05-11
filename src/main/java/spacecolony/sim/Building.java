package spacecolony.sim;

public class Building {
    public final BuildingType type;
    public int level;
    /** False when disabled by an event (e.g. equipment failure). */
    public boolean enabled;

    public Building(BuildingType type, int level) {
        this.type = type;
        this.level = level;
        this.enabled = true;
    }
}
