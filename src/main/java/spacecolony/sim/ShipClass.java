package spacecolony.sim;

public enum ShipClass {
    HAULER(50.0, 200.0, 0.5),
    TANKER(40.0, 300.0, 0.4),
    COLONIZER(80.0, 100.0, 0.3);

    private final double dryMass;
    private final double cargoCap;
    private final double speed; // AU per tick

    ShipClass(double dryMass, double cargoCap, double speed) {
        this.dryMass = dryMass;
        this.cargoCap = cargoCap;
        this.speed = speed;
    }

    public double dryMass() { return dryMass; }
    public double cargoCap() { return cargoCap; }
    public double speed() { return speed; }
}
