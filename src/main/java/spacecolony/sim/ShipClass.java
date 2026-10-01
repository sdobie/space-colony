package spacecolony.sim;

public enum ShipClass {
    HAULER(50.0, 200.0, 0.5, 0.0),
    TANKER(40.0, 300.0, 0.4, 0.0),
    COLONIZER(80.0, 100.0, 0.3, 0.0),
    /** Surveys the bodies it reaches (Plan 9); no cargo, carries its own tank between bodies. */
    EXPLORER(20.0, 0.0, 0.8, 100.0);

    private final double dryMass;
    private final double cargoCap;
    private final double speed; // AU per tick
    private final double tankCap;

    ShipClass(double dryMass, double cargoCap, double speed, double tankCap) {
        this.dryMass = dryMass;
        this.cargoCap = cargoCap;
        this.speed = speed;
        this.tankCap = tankCap;
    }

    public double dryMass() { return dryMass; }
    public double cargoCap() { return cargoCap; }
    public double speed() { return speed; }
    /**
     * FUEL the ship tops its tank up to when leaving a colony, so it can fly on from orbit.
     * 0 means it draws only each trip's fuel.
     */
    public double tankCap() { return tankCap; }
}
