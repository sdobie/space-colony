package spacecolony.sim;

/** The three sizes of meteor strike (Plan 11 §4.1). */
public enum MeteorSize {
    //           chance bldgs store  stores deaths morale farm  dust days
    MINOR       (0.60,  1,   0.10,  1,     0.00,  0.0,   1.00, 0),
    MAJOR       (0.30,  2,   0.20,  2,     0.02,  0.1,   0.75, 20),
    CATASTROPHIC(0.10,  3,   0.30,  3,     0.05,  0.2,   0.50, 60);

    /** Chance a strike is this size. */
    public final double chance;
    /** Distinct working buildings damaged. */
    public final int buildings;
    /** Share of each hit stock destroyed. */
    public final double storeShare;
    /** Distinct stocks hit. */
    public final int stores;
    /** Share of the population killed (at least 1 when above 0 and anyone lives there). */
    public final double deathShare;
    /** Morale lost. */
    public final double moraleDrop;
    /** Farm FOOD factor under impact dust; 1 means no dust. */
    public final double farmFactor;
    /** Days of impact dust. */
    public final int dustDays;

    MeteorSize(double chance, int buildings, double storeShare, int stores, double deathShare,
               double moraleDrop, double farmFactor, int dustDays) {
        this.chance = chance;
        this.buildings = buildings;
        this.storeShare = storeShare;
        this.stores = stores;
        this.deathShare = deathShare;
        this.moraleDrop = moraleDrop;
        this.farmFactor = farmFactor;
        this.dustDays = dustDays;
    }

    /** "Meteor strike", "Major meteor strike", "Catastrophic meteor strike". */
    public String title() {
        return switch (this) {
            case MINOR -> "Meteor strike";
            case MAJOR -> "Major meteor strike";
            case CATASTROPHIC -> "Catastrophic meteor strike";
        };
    }

    /** Picks a size from a uniform draw in [0, 1). */
    public static MeteorSize roll(double u) {
        double acc = 0;
        for (MeteorSize m : values()) {
            acc += m.chance;
            if (u < acc) return m;
        }
        return CATASTROPHIC;
    }
}
