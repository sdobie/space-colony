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
    /**
     * Days a power plant stays offline after a solar flare (Plan 11 §4.4). Not damage: it counts
     * down by itself in ProductionPhase and needs no repair.
     */
    public int offlineDays;
    /** The event that damaged this building, or null (undamaged, or damaged before Plan 11). */
    public EventKind damagedBy;
    /** The tick {@link #damagedBy} happened; meaningful only while it is set. */
    public long damagedTick;

    public Building(BuildingType type, int level) {
        this.type = type;
        this.level = level;
        this.enabled = true;
    }

    /** Built, not damaged and not knocked offline: does its job today. */
    public boolean isOperational() { return enabled && offlineDays == 0 && level > 0; }

    public boolean isUnderConstruction() { return daysLeft > 0; }

    /** Built and not damaged: something an event can still break (an offline plant counts). */
    public boolean isIntact() { return enabled && level > 0; }

    /** Damaged by {@code kind} at {@code tick}: off until repaired. */
    public void damage(EventKind kind, long tick) {
        enabled = false;
        damagedBy = kind;
        damagedTick = tick;
    }

    /** Repaired: working again, with no damage cause. */
    public void repair() {
        enabled = true;
        damagedBy = null;
        damagedTick = 0;
    }
}
