package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Root game state. Mutated only by {@link Simulator#advance(World)} (which itself runs
 * commands the engine has enqueued). The UI never mutates this directly.
 */
public class World {
    public long tick;
    public final long seed;
    public final List<Body> bodies = new ArrayList<>();
    public final List<Ship> ships = new ArrayList<>();
    public final TechState tech = new TechState();
    public final GoalState goals = new GoalState();
    /** Capped ring of recent events; UI displays the tail. */
    public final Deque<Event> recentEvents = new ArrayDeque<>();
    public static final int MAX_RECENT_EVENTS = 200;
    public long credits;

    public World(long seed) {
        this.seed = seed;
        this.tick = 0;
        this.credits = 10_000;
    }

    public void emit(Event e) {
        recentEvents.addLast(e);
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
    }

    public Body findBody(String id) {
        for (Body b : bodies) if (b.id.equals(id)) return b;
        return null;
    }
    public Site findSite(String siteId) {
        for (Body b : bodies) for (Site s : b.sites) if (s.id.equals(siteId)) return s;
        return null;
    }
    public Ship findShip(String shipId) {
        for (Ship s : ships) if (s.id.equals(shipId)) return s;
        return null;
    }
}
