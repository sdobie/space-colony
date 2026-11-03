package spacecolony.sim;

/** Test helper: finishes every ship under construction at once (Plan 10 build times). */
public final class TestShips {
    private TestShips() {}

    public static void finishBuilds(World w) {
        for (Ship s : w.ships) {
            if (s.state == ShipState.BUILDING) {
                s.state = ShipState.IDLE;
                s.buildDaysLeft = 0;
            }
        }
    }
}
