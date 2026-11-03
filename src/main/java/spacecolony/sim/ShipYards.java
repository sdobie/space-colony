package spacecolony.sim;

/** Which shipyards at a colony are free, and what a ship takes there (Plan 10 §3). */
public final class ShipYards {
    private ShipYards() {}

    /** The ship {@code yard} at {@code s} is building, or null. */
    public static Ship building(World w, Site s, Building yard) {
        for (Ship sh : w.ships)
            if (sh.state == ShipState.BUILDING && s.id.equals(sh.currentSiteId) && sh.yardBuildingId == yard.id)
                return sh;
        return null;
    }

    /** The operational shipyard at {@code s} with no ship on it, highest level first; null if none. */
    public static Building freeYard(World w, Site s) {
        Building best = null;
        for (Building b : s.buildings) {
            if (b.type != BuildingType.SHIPYARD || !b.isOperational() || building(w, s, b) != null) continue;
            if (best == null || b.level > best.level) best = b;
        }
        return best;
    }

    /** Calendar days a {@code c} takes at a level-{@code level} shipyard. */
    public static int days(ShipClass c, int level) {
        return days(c.buildDays(), level);
    }

    /** Calendar days {@code workDays} of work take at a level-{@code level} shipyard. */
    public static int days(int workDays, int level) {
        int l = Math.max(1, level);
        return (workDays + l - 1) / l;
    }

    /** Why {@code s} can't start a {@code c} now (no yard, all busy, or can't pay), or null. */
    public static String whyNotBuild(World w, Site s, ShipClass c) {
        boolean any = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.isOperational());
        if (!any) return s.name + " has no working shipyard";
        if (freeYard(w, s) == null) {
            Ship first = null;
            for (Ship sh : w.ships)
                if (sh.state == ShipState.BUILDING && s.id.equals(sh.currentSiteId)) { first = sh; break; }
            return "Every shipyard at " + s.name + " is busy"
                + (first != null ? " (" + first.name + ", " + daysLeftText(w, first) + ")" : "");
        }
        return Construction.shortfallText(c.cost(), s.stockpile);
    }

    /** The shipyard building {@code ship}, or null if it's gone. */
    public static Building yardOf(World w, Ship ship) {
        Site s = w.findSite(ship.currentSiteId);
        return s == null ? null : s.findBuilding(ship.yardBuildingId);
    }

    /** Calendar days until {@code ship} is done at its yard's current level. */
    public static int calendarDaysLeft(World w, Ship ship) {
        Building yard = yardOf(w, ship);
        return days(ship.buildDaysLeft, yard == null ? 1 : yard.level);
    }

    /** "12 days left" / "1 day left". */
    public static String daysLeftText(World w, Ship ship) {
        int d = calendarDaysLeft(w, ship);
        return d + (d == 1 ? " day left" : " days left");
    }

    /** Share of the work done, 0..1. */
    public static double progress(Ship ship) {
        int total = ship.shipClass.buildDays();
        return total <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, 1.0 - ship.buildDaysLeft / (double) total));
    }
}
