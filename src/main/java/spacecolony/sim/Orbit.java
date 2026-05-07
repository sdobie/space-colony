package spacecolony.sim;

/**
 * Circular-orbit approximation. Heliocentric for top-level bodies; if {@code parentBodyId}
 * is non-null, this orbit is around that parent (used for moons).
 */
public record Orbit(
    double semiMajorAxis,
    long period,
    double phaseOffset,
    String parentBodyId
) {
    /**
     * @return {x, y} position at the given tick, in the orbit's local frame
     *   (i.e. relative to the Sun for top-level bodies, relative to the parent for moons).
     */
    public double[] position(long tick) {
        double angle = phaseOffset + 2.0 * Math.PI * tick / period;
        return new double[] {
            semiMajorAxis * Math.cos(angle),
            semiMajorAxis * Math.sin(angle)
        };
    }
}
