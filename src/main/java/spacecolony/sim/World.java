package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

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
    /**
     * When false, {@code EventPhase.run} rolls no random events. Set at creation (the tutorial
     * world) or by {@code SetRandomEventsCommand}; forced events ignore it.
     */
    public boolean randomEventsEnabled = true;
    /** Ids of bodies whose resources the player has seen (Plan 9). Sorted for stable saves. */
    public final SortedSet<String> surveyedBodies = new TreeSet<>();

    public World(long seed) {
        this.seed = seed;
        this.tick = 0;
        this.credits = 10_000;
    }

    public void emit(Event e) {
        recentEvents.addLast(e);
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
    }

    public boolean isSurveyed(String bodyId) {
        return bodyId != null && surveyedBodies.contains(bodyId);
    }

    /**
     * Marks {@code bodyId} surveyed by {@code byWhom} and, if it wasn't already, emits a
     * BODY_SURVEYED event naming its best ground. Returns whether the survey was new.
     */
    public boolean survey(String bodyId, String byWhom) {
        Body b = findBody(bodyId);
        if (b == null || !surveyedBodies.add(bodyId)) return false;
        List<ResourceSurvey.Entry> entries = ResourceSurvey.of(b);
        StringBuilder sb = new StringBuilder(byWhom + " surveyed " + b.name + ". ");
        if (entries.isEmpty()) {
            sb.append("No useful resources.");
        } else {
            sb.append("Best spots: ");
            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(entries.get(i).resource().name()).append(' ')
                  .append(ResourceSurvey.fmt(entries.get(i).best()));
            }
            sb.append('.');
        }
        emit(new Event(tick, EventSeverity.INFO, EventKind.BODY_SURVEYED, sb.toString(), bodyId, null, null));
        return true;
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
