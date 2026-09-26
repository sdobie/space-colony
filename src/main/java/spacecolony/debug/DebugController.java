package spacecolony.debug;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.phases.EventPhase;

/**
 * Engine-side half of debug mode (spec §8): phase timings, tick-rate measurement,
 * single-tick stepping, Run N ticks, forced events, and mirroring the world's event log
 * into java.util.logging so the log viewer shows one merged stream. EDT-only.
 */
public final class DebugController {
    private static final Logger EVENTS_LOG = Logger.getLogger(DebugLogging.ROOT + ".sim.events");
    static {
        // Game events belong in the log viewer, not echoed to the terminal on every tick.
        EVENTS_LOG.setUseParentHandlers(false);
        EVENTS_LOG.addHandler(DebugLogging.buffer());
    }
    private static final Logger LOG = Logger.getLogger(DebugController.class.getName());

    public static final int MAX_RUN_TICKS = 10_000;

    private final Engine engine;
    private final PhaseTimings timings = new PhaseTimings();
    private final TickRateMeter tickRate = new TickRateMeter();
    private final LongSupplier clock;
    private final EngineListener listener = this::onEvent;
    /** Last world event already mirrored to the log, compared by identity. */
    private Event lastMirrored;

    public DebugController(Engine engine) { this(engine, System::nanoTime); }

    DebugController(Engine engine, LongSupplier clock) {
        EdtGuard.assertEdt();
        this.engine = engine;
        this.clock = clock;
        engine.setPhaseObserver(timings);
        engine.addListener(listener);
        lastMirrored = engine.world().recentEvents.peekLast();
    }

    public Engine engine() { return engine; }
    public PhaseTimings timings() { return timings; }

    public double ticksPerSecond() { return tickRate.ticksPerSecond(clock.getAsLong()); }

    public void setDebugEnabled(boolean on) { engine.setDebugEnabled(on); }
    public void toggleDebug() { engine.setDebugEnabled(!engine.debugEnabled()); }

    /** Advance exactly one tick. Only allowed while paused, per spec. */
    public void stepOne() {
        EdtGuard.assertEdt();
        if (!engine.speed().isPaused()) throw new IllegalStateException("Step 1 tick requires the game to be paused");
        engine.tick();
    }

    /** Advance {@code n} ticks without repainting between them. */
    public void runTicks(int n) {
        EdtGuard.assertEdt();
        if (n <= 0 || n > MAX_RUN_TICKS) {
            throw new IllegalArgumentException("Tick count must be 1.." + MAX_RUN_TICKS + ", was " + n);
        }
        long start = System.nanoTime();
        engine.advanceTicks(n);
        long ms = (System.nanoTime() - start) / 1_000_000;
        LOG.info(() -> "Ran " + n + " ticks in " + ms + " ms (now tick " + engine.world().tick + ")");
    }

    /** Force-roll {@code kind} on the given body immediately. Paused only. */
    public void triggerEvent(String bodyId, EventKind kind) {
        EdtGuard.assertEdt();
        Body b = engine.world().findBody(bodyId);
        if (b == null) throw new IllegalArgumentException("No body with id " + bodyId);
        engine.applyDebugEdit("trigger " + kind + " on " + b.id + " at tick " + engine.world().tick,
            w -> EventPhase.force(w, b, kind));
    }

    /** The live sim object a selection points at, or null. */
    public static Object resolve(Engine engine, Selection sel) {
        return switch (sel.kind()) {
            case BODY -> engine.world().findBody(sel.id());
            case SITE -> engine.world().findSite(sel.id());
            case SHIP -> engine.world().findShip(sel.id());
            case NONE -> null;
        };
    }

    public void dispose() {
        engine.removeListener(listener);
        engine.setPhaseObserver(null);
    }

    private void onEvent(EngineEvent e) {
        if (e instanceof EngineEvent.WorldChanged wc) {
            tickRate.record(clock.getAsLong(), wc.tick());
            mirrorNewEvents();
        } else if (e instanceof EngineEvent.WorldReplaced) {
            tickRate.reset();
            timings.clear();
            lastMirrored = engine.world().recentEvents.peekLast();
        }
    }

    private void mirrorNewEvents() {
        var events = engine.world().recentEvents;
        // Walk back from the newest event until we hit the last one we logged.
        ArrayDeque<Event> fresh = new ArrayDeque<>();
        for (Iterator<Event> it = events.descendingIterator(); it.hasNext(); ) {
            Event ev = it.next();
            if (ev == lastMirrored) break;
            fresh.addFirst(ev);
        }
        for (Event ev : fresh) EVENTS_LOG.log(levelFor(ev.severity()), "[t=" + ev.tick() + "] " + ev.kind() + ": " + ev.message());
        if (!fresh.isEmpty()) lastMirrored = fresh.peekLast();
    }

    private static Level levelFor(EventSeverity s) {
        return switch (s) {
            case INFO -> Level.INFO;
            case WARNING -> Level.WARNING;
            case ERROR -> Level.SEVERE;
        };
    }
}
