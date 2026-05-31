package spacecolony.sim;

/** Stateless heliocentric coordinate helpers shared by transit and production phases. */
public final class OrbitalGeometry {
    private OrbitalGeometry() {}

    /** Heliocentric (x, y) position of a body at the given tick, accounting for moon parents. */
    public static double[] bodyPosition(World w, String bodyId, long tick) {
        Body b = w.findBody(bodyId);
        if (b == null) return new double[] {0, 0};
        double[] p = b.orbit.position(tick);
        if (b.orbit.parentBodyId() != null) {
            double[] parent = bodyPosition(w, b.orbit.parentBodyId(), tick);
            return new double[] { parent[0] + p[0], parent[1] + p[1] };
        }
        return p;
    }

    /** Distance from a body to the sun (origin) at the world's current tick. */
    public static double sunDistance(World w, Body b) {
        double[] p = bodyPosition(w, b.id, w.tick);
        return Math.sqrt(p[0] * p[0] + p[1] * p[1]);
    }
}
