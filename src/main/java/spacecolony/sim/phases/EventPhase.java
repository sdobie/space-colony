package spacecolony.sim.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import spacecolony.sim.Body;
import spacecolony.sim.BodyType;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.DeterministicRng;
import spacecolony.sim.Event;
import spacecolony.sim.EventEffects;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.MeteorSize;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.World;
import spacecolony.sim.economy.FlowLine;
import spacecolony.sim.economy.FlowSource;

/**
 * Random events (Plan 11). Meteor strikes, equipment failures and disease outbreaks roll per
 * colony and fire only when they would do something there; the solar flare rolls once for the
 * whole system. Every event states its consequences in its message.
 */
public final class EventPhase {
    /** Chance per colony per day of a meteor strike, equipment failure or disease outbreak. */
    public static final double COLONY_EVENT_RATE = 0.004;
    /** Chance per day of a system-wide solar flare. */
    public static final double FLARE_RATE = 0.0015;
    /** A meteor only takes from a stock holding at least this much. */
    static final double MIN_STORE = 5.0;
    /** Flare outage at 1 AU; scales as 1/r², clamped to [MIN, MAX] days. */
    static final double FLARE_DAYS_AT_1AU = 3.0;
    static final int FLARE_MIN_DAYS = 1;
    static final int FLARE_MAX_DAYS = 6;

    private static final EventKind[] RANDOM_KINDS = {
        EventKind.METEOR_STRIKE, EventKind.SOLAR_FLARE,
        EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK
    };

    private EventPhase() {}

    public static void run(World w) {
        Random rng = DeterministicRng.forStep(w.seed, w.tick, 6L);
        if (!w.randomEventsEnabled) return;
        if (rng.nextDouble() < FLARE_RATE && anyIntactPowerPlant(w)) solarFlare(w);
        for (Body b : w.bodies) {
            for (Site s : List.copyOf(b.sites)) {
                if (rng.nextDouble() >= COLONY_EVENT_RATE) continue;
                EventKind k = pickApplicable(b, s, rng);
                if (k != null) applyAt(w, b, s, k, null, rng);
            }
        }
    }

    /** RNG step id reserved for debug-forced events; never used by a sim phase. */
    public static final long DEBUG_STEP_ID = 99L;

    /**
     * Apply {@code kind} to the first colony on {@code b} (or, if it has none, report it on the
     * body with no damage), bypassing the random draw. A solar flare ignores the target.
     */
    public static void applyForced(World w, Body b, EventKind kind, Random rng) {
        applyForced(w, b, b.sites.isEmpty() ? null : b.sites.get(0), kind, null, rng);
    }

    /**
     * Apply {@code kind} at {@code site} now, used by the debug "Trigger event…" control and tests.
     * {@code size} picks a meteor's size (null rolls it). Fires even when it does nothing, so the
     * debug control never looks broken.
     */
    public static void applyForced(World w, Site site, EventKind kind, MeteorSize size, Random rng) {
        applyForced(w, site == null ? null : w.findBody(site.bodyId), site, kind, size, rng);
    }

    private static void applyForced(World w, Body b, Site s, EventKind kind, MeteorSize size, Random rng) {
        if (kind == EventKind.SOLAR_FLARE) { solarFlare(w); return; }
        if (s == null) {
            w.emit(new Event(w.tick, EventSeverity.WARNING, kind, new EventEffects().message(title(kind), null, b),
                b == null ? null : b.id, null, null));
            return;
        }
        applyAt(w, b, s, kind, size, rng);
    }

    /** The event kinds {@link #run} can roll (the ones the debug trigger offers). */
    public static List<EventKind> randomKinds() { return List.of(RANDOM_KINDS); }

    /** Bodies with no air to hold impact dust, where meteors also fall twice as often. */
    public static boolean isAirless(Body b) {
        return b.type == BodyType.ASTEROID || b.type == BodyType.MOON || b.type == BodyType.ICE_BODY;
    }

    /** Solar-flare outage at a body: ceil(3 / r²) days, 1 to 6. */
    public static int flareDays(World w, Body b) {
        double r = OrbitalGeometry.sunDistance(w, b);
        int days = (int) Math.ceil(FLARE_DAYS_AT_1AU / Math.max(1e-6, r * r) - 1e-9);
        return Math.max(FLARE_MIN_DAYS, Math.min(FLARE_MAX_DAYS, days));
    }

    /** A body event that would do something at {@code s}, weighted per Plan 11 §3; null if none. */
    static EventKind pickApplicable(Body b, Site s, Random rng) {
        double meteor = canMeteorHit(s) ? (isAirless(b) ? 2.0 : 1.0) : 0.0;
        double failure = intact(s).isEmpty() ? 0.0 : 1.0;
        double disease = s.population > 0 ? 1.0 : 0.0;
        double total = meteor + failure + disease;
        if (total <= 0) return null;
        double u = rng.nextDouble() * total;
        if (u < meteor) return EventKind.METEOR_STRIKE;
        if (u < meteor + failure) return EventKind.EQUIPMENT_FAILURE;
        return EventKind.DISEASE_OUTBREAK;
    }

    private static void applyAt(World w, Body b, Site s, EventKind k, MeteorSize size, Random rng) {
        EventEffects fx = new EventEffects();
        String title = title(k);
        boolean catastrophic = false;
        switch (k) {
            case METEOR_STRIKE -> {
                MeteorSize m = size != null ? size : MeteorSize.roll(rng.nextDouble());
                title = m.title();
                catastrophic = m == MeteorSize.CATASTROPHIC;
                meteor(w, b, s, m, rng, fx);
            }
            case EQUIPMENT_FAILURE -> {
                List<Building> pool = intact(s);
                if (!pool.isEmpty()) {
                    Building hit = pool.get(rng.nextInt(pool.size()));
                    hit.damage(k, w.tick);
                    fx.damaged(hit, "broke down");
                }
            }
            case DISEASE_OUTBREAK -> {
                if (s.population > 0) {
                    // Truncating (not rounding) keeps severity=1.0 identical to pre-tech behaviour.
                    double severity = TechEffects.diseaseSeverityMultiplier(w.tech);
                    int loss = Math.min(s.population, (int) Math.max(1, (s.population / 10.0) * severity));
                    s.population -= loss;
                    double before = s.morale;
                    s.morale = Math.max(0, s.morale - 0.2 * severity);
                    fx.died(loss);
                    fx.morale(before - s.morale);
                }
            }
            default -> {}
        }
        EventSeverity sev = fx.anyDeaths() || catastrophic ? EventSeverity.ERROR : EventSeverity.WARNING;
        w.emit(new Event(w.tick, sev, k, fx.message(title, s, b), b.id, s.id, null));
    }

    private static void meteor(World w, Body b, Site s, MeteorSize m, Random rng, EventEffects fx) {
        // Buildings: distinct intact ones (built, not already damaged).
        List<Building> pool = intact(s);
        for (int i = 0; i < m.buildings && !pool.isEmpty(); i++) {
            Building hit = pool.remove(rng.nextInt(pool.size()));
            hit.damage(EventKind.METEOR_STRIKE, w.tick);
            fx.damaged(hit, "damaged");
        }
        // Stores: distinct stocks holding at least MIN_STORE, in Resource order.
        List<Resource> stocks = stocks(s);
        for (int i = 0; i < m.stores && !stocks.isEmpty(); i++) {
            Resource r = stocks.remove(rng.nextInt(stocks.size()));
            double have = s.stockpile.getOrDefault(r, 0.0);
            double lost = Math.min(have, Math.max(1.0, Math.floor(have * m.storeShare + 1e-9)));
            s.stockpile.put(r, have - lost);
            fx.destroyed(r, lost);
            // Yesterday's ledger carries the loss, so the stock change still adds up (Plan 7).
            if (s.lastDay != null)
                s.lastDay.add(new FlowLine(new FlowSource.EventLoss(EventKind.METEOR_STRIKE), r, -lost, 0.0));
        }
        // People.
        if (m.deathShare > 0 && s.population > 0) {
            int killed = Math.min(s.population, Math.max(1, (int) Math.floor(s.population * m.deathShare)));
            s.population -= killed;
            fx.died(killed);
        }
        if (m.moraleDrop > 0 && s.population > 0) {
            double before = s.morale;
            s.morale = Math.max(0, s.morale - m.moraleDrop);
            if (before - s.morale > 1e-9) fx.morale(before - s.morale);
        }
        // Impact dust, where there is air to hold it and a farm to dim.
        boolean hasFarm = s.buildings.stream().anyMatch(bd -> bd.type == BuildingType.FARM);
        if (m.dustDays > 0 && !isAirless(b) && hasFarm) {
            boolean dusty = s.dustDays > 0;
            s.dustFactor = dusty ? Math.min(s.dustFactor, m.farmFactor) : m.farmFactor;
            s.dustDays = Math.max(s.dustDays, m.dustDays);
            fx.dust(m.farmFactor, m.dustDays);
        }
    }

    private static void solarFlare(World w) {
        EventEffects fx = new EventEffects();
        for (Body b : w.bodies) {
            for (Site s : b.sites) {
                int days = flareDays(w, b);
                boolean hit = false;
                for (Building bd : s.buildings) {
                    if (bd.type != BuildingType.POWER_PLANT || !bd.isIntact()) continue;
                    bd.offlineDays = Math.max(bd.offlineDays, days);
                    hit = true;
                }
                if (hit) fx.offline(s, days);
            }
        }
        w.emit(new Event(w.tick, EventSeverity.WARNING, EventKind.SOLAR_FLARE,
            fx.message(title(EventKind.SOLAR_FLARE), null, null), null, null, null));
    }

    private static boolean canMeteorHit(Site s) {
        return !intact(s).isEmpty() || !stocks(s).isEmpty() || s.population > 0;
    }

    private static boolean anyIntactPowerPlant(World w) {
        for (Body b : w.bodies) for (Site s : b.sites)
            for (Building bd : s.buildings) if (bd.type == BuildingType.POWER_PLANT && bd.isIntact()) return true;
        return false;
    }

    private static List<Building> intact(Site s) {
        List<Building> out = new ArrayList<>();
        for (Building bd : s.buildings) if (bd.isIntact()) out.add(bd);
        return out;
    }

    private static List<Resource> stocks(Site s) {
        List<Resource> out = new ArrayList<>();
        for (Resource r : Resource.values())
            if (r.isStockpileable() && s.stockpile.getOrDefault(r, 0.0) >= MIN_STORE) out.add(r);
        return out;
    }

    /** "Meteor strike", "Solar flare", "Equipment failure", "Disease outbreak". */
    public static String title(EventKind k) {
        String n = k.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
