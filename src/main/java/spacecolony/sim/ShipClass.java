package spacecolony.sim;

public enum ShipClass {
    HAULER(50.0, 200.0, 0.065, 0.0),
    TANKER(40.0, 300.0, 0.05, 0.0),
    COLONIZER(80.0, 100.0, 0.04, 0.0),
    /** Surveys the bodies it reaches (Plan 9); no cargo, carries its own tank between bodies. */
    EXPLORER(20.0, 0.0, 0.1, 100.0);

    private final double dryMass;
    private final double cargoCap;
    /**
     * AU per tick (day). Earth to Mars is 0.7 to 2.5 AU, so a hauler takes about 2 to 6 weeks
     * and a colonizer 3 to 9; Jovian is months away.
     */
    private final double speed;
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
