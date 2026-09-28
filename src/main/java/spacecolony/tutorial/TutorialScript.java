package spacecolony.tutorial;

import java.util.List;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.World;

/**
 * The tutorial's 12 steps (Plan 6 §5.3): run the clock, read a colony, research, build a
 * colonizer, fly it to Mars and found a colony there. Completion tests look only at game state,
 * so a player learns by using the real controls.
 */
public final class TutorialScript {
    // Component names the panels set (their TARGET_* constants); `tutorial` can't import `ui`,
    // so TutorialTargetsTest checks that these copies match.
    public static final String T_X1 = "topbar.speed.x1";
    public static final String T_X16 = "topbar.speed.x16";
    public static final String T_TECH = "topbar.tech";
    public static final String T_GOALS = "topbar.goals";
    public static final String T_COLONY_LIST = "colonylist";
    public static final String T_DETAIL = "detail";
    public static final String T_BUILD_BUILDING = "detail.buildBuilding";
    public static final String T_BUILD_SHIP = "detail.buildShip";
    public static final String T_DISPATCH = "detail.dispatch";
    public static final String T_FOUND_COLONY = "detail.foundColony";
    public static final String T_SPHERE = "bodyview.sphere";

    public static final String HUB = "site-earth-hub";

    private TutorialScript() {}

    public static List<TutorialStep> steps() {
        return List.of(
            manual("welcome", "Welcome to Space Colony",
                "<p>You run a young space program with one base, <b>Earth Hub</b>. The goal is a network"
                + " of colonies across the solar system, kept alive by the ships you send between them.</p>"
                + "<p>The game is paused. This tutorial walks you through your first new colony.</p>",
                List.of()),
            auto("start-clock", "Start the clock",
                "<p>Press <b>1×</b> in the top bar to start time. One tick is one day. The faster speeds"
                + " and pause sit next to it.</p>",
                List.of(T_X1), null,
                c -> c.speed() != Speed.PAUSED),
            auto("select-hub", "Look at your base",
                "<p>Click <b>Earth Hub</b> in the colony list on the left.</p>",
                List.of(T_COLONY_LIST), null,
                c -> Selection.site(HUB).equals(c.selection())),
            manual("read-dock", "Reading a colony",
                "<p>The panel on the right shows the selected colony: population against its cap,"
                + " morale, each resource's stock, its net change per day and how long it will last.</p>"
                + "<p>Click a resource to see what makes and uses it. Keep an eye on food and water."
                + " When they run out, morale and population fall.</p>",
                List.of(T_DETAIL)),
            auto("build-lab", "Build a research lab",
                "<p>Research needs a lab. With Earth Hub selected, click <b>Build building...</b>,"
                + " pick <b>Research lab</b> and press <b>Build</b>. The card on the right shows what"
                + " each building makes and uses, and what it would change at this colony.</p>",
                List.of(T_BUILD_BUILDING), "Select Earth Hub in the colony list first.",
                c -> hasBuilding(c.world(), BuildingType.RESEARCH_LAB)),
            auto("research", "Start researching",
                "<p>Open <b>Tech</b> in the top bar and click any tech without a lock. Research points"
                + " come from your labs every day.</p>",
                List.of(T_TECH), null,
                c -> c.world().tech.activeId != null || !c.world().tech.researched.isEmpty()),
            auto("build-colonizer", "Build a colonizer",
                "<p>New colonies start with a colonizer ship. With Earth Hub selected, click"
                + " <b>Build ship...</b>, choose <b>COLONIZER</b> and press OK.</p>",
                List.of(T_BUILD_SHIP), "Select Earth Hub in the colony list first.",
                c -> hasColonizer(c.world()) || settledBeyondEarth(c.world())),
            auto("dispatch", "Send it to Mars",
                "<p>Select your colonizer in the colony list and click <b>Dispatch...</b>, then choose"
                + " <b>Mars (unsettled)</b> and pack FOOD 40, WATER 40 and METAL 20: whatever it carries"
                + " becomes the new colony's first stock.</p>"
                + "<p>Fuel for the trip comes from Earth Hub's stockpile when the ship leaves.</p>",
                List.of(T_DISPATCH), "Select your colonizer in the colony list.",
                c -> colonizerHeadingOut(c.world()) || orbiting(c.world()) || settledBeyondEarth(c.world())),
            auto("fast-forward", "Speed things up",
                "<p>Loading and the flight take a few days. Try <b>16×</b> while you wait.</p>",
                List.of(T_X16), null,
                c -> orbiting(c.world()) || settledBeyondEarth(c.world())),
            auto("found-colony", "Found the colony",
                "<p>Your colonizer is waiting in orbit. Select it, click <b>Found colony…</b>, then click"
                + " a spot on the planet's surface and press OK.</p>",
                List.of(T_FOUND_COLONY, T_SPHERE), "Select your colonizer, or open Mars's body view.",
                c -> settledBeyondEarth(c.world())),
            manual("goals", "Goals",
                "<p>Your first colony just completed a goal and earned a reward. <b>Goals</b> in the top"
                + " bar lists the rest. None of them end the game.</p>",
                List.of(T_GOALS)),
            manual("finish", "You're ready",
                "<p>That's the core loop: build, ship, settle, and keep every colony supplied. Build a"
                + " FARM on your new colony so it can feed itself.</p>"
                + "<p>Press <b>Keep playing</b> to carry on with this game (random events switch on and"
                + " you can save), or <b>Main menu</b> to leave.</p>",
                List.of()));
    }

    private static TutorialStep manual(String id, String title, String html, List<String> targets) {
        return new TutorialStep(id, title, html, targets, null, null);
    }

    private static TutorialStep auto(String id, String title, String html, List<String> targets, String hint,
                                     java.util.function.Predicate<TutorialContext> done) {
        return new TutorialStep(id, title, html, targets, hint, done);
    }

    private static boolean hasBuilding(World w, BuildingType type) {
        Site hub = w.findSite(HUB);
        return hub != null && hub.buildings.stream().anyMatch(b -> b.type == type);
    }

    private static boolean hasColonizer(World w) {
        return w.ships.stream().anyMatch(s -> s.shipClass == ShipClass.COLONIZER);
    }

    /** A colonizer loading for, or flying to, a body with no site. */
    private static boolean colonizerHeadingOut(World w) {
        for (Ship s : w.ships) {
            if (s.shipClass != ShipClass.COLONIZER || s.transit == null) continue;
            if (s.transit.destBodyId() != null
                && (s.state == ShipState.LOADING || s.state == ShipState.IN_TRANSIT)) return true;
        }
        return false;
    }

    private static boolean orbiting(World w) {
        return w.ships.stream().anyMatch(s -> s.orbitingBodyId != null);
    }

    private static boolean settledBeyondEarth(World w) {
        return w.bodies.stream().anyMatch(b -> !b.id.equals("earth") && !b.sites.isEmpty());
    }
}
