package spacecolony.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import spacecolony.sim.Event;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.PhaseObserver;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.sim.commands.Command;

/**
 * Top-level engine. Wraps a World + Simulator and notifies UI listeners on changes.
 * UI calls {@link #enqueue} to submit player commands; the next {@link #tick} drains them.
 *
 * <p>The engine is also the logging bridge: the sim stays logging-free, and the engine logs
 * each new world {@link Event} to {@code spacecolony.sim.events} plus its own actions.
 *
 * Not thread-safe — meant to be driven from the EDT.
 */
public class Engine {
    private static final Logger LOG = Logger.getLogger("spacecolony.engine");
    private static final Logger EVENTS = Logger.getLogger("spacecolony.sim.events");
    private static final Logger DEBUG = Logger.getLogger("spacecolony.debug");

    public static final int MAX_SILENT_TICKS = 10_000;

    private World world;
    private final Simulator simulator = new Simulator();
    private final List<EngineListener> listeners = new ArrayList<>();
    private Selection selection = Selection.NONE;
    private Speed speed = Speed.PAUSED;
    private EngineEvent.ViewChanged.View view = EngineEvent.ViewChanged.View.SYSTEM_MAP;
    private boolean debugEnabled;
    private final long[] tickTimes = new long[64];   // ring of System.nanoTime() at each tick
    private int tickCount;
    /** Newest world event already logged, compared by identity. */
    private Event lastLogged;

    public Engine(World world) {
        this.world = world;
        this.lastLogged = world.recentEvents.peekLast();
    }

    public World world() { return world; }
    public Selection selection() { return selection; }
    public Speed speed() { return speed; }
    public EngineEvent.ViewChanged.View view() { return view; }
    public boolean debugEnabled() { return debugEnabled; }

    public void addListener(EngineListener l) {
        EdtGuard.assertEdt();
        listeners.add(l);
    }
    public void removeListener(EngineListener l) {
        EdtGuard.assertEdt();
        listeners.remove(l);
    }

    public void enqueue(Command c) {
        EdtGuard.assertEdt();
        LOG.fine(() -> "Enqueue " + c);
        simulator.enqueue(c);
    }

    /** Advance the simulator one tick, then fire WorldChanged. */
    public void tick() {
        EdtGuard.assertEdt();
        simulator.advance(world);
        logNewEvents();
        tickTimes[tickCount++ % tickTimes.length] = System.nanoTime();
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /** Advance exactly one tick while paused (debug "Step"). */
    public void step() {
        EdtGuard.assertEdt();
        assert speed.isPaused() : "step() requires PAUSED";
        tick();
    }

    /** Advance {@code n} ticks, firing a single WorldChanged at the end (debug "Run N"). */
    public void advanceSilently(int n) {
        EdtGuard.assertEdt();
        if (n < 1 || n > MAX_SILENT_TICKS) {
            throw new IllegalArgumentException("n must be 1.." + MAX_SILENT_TICKS + ", was " + n);
        }
        for (int i = 0; i < n; i++) {
            simulator.advance(world);
            logNewEvents();
        }
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /**
     * The only path by which anything outside the Simulator mutates World. Debug-only;
     * requires PAUSED so edits never interleave with ticks. Logs {@code description} so a
     * changed world can always be traced to the edit that changed it.
     */
    public void applyDebugEdit(String description, Consumer<World> fn) {
        EdtGuard.assertEdt();
        assert speed.isPaused() : "debug edits require PAUSED";
        fn.accept(world);
        DEBUG.info(() -> "Debug edit at tick " + world.tick + ": " + description);
        logNewEvents();
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    public int commandQueueDepth() { return simulator.queueDepth(); }

    public void setPhaseObserver(PhaseObserver o) {
        EdtGuard.assertEdt();
        simulator.setPhaseObserver(o);
    }

    /** Measured ticks per real second over the recorded ring (0 when fewer than 2 ticks). */
    public double ticksPerSecond() {
        int n = Math.min(tickCount, tickTimes.length);
        if (n < 2) return 0.0;
        long newest = tickTimes[(tickCount - 1) % tickTimes.length];
        long oldest = tickTimes[(tickCount - n) % tickTimes.length];
        if (newest == oldest) return 0.0;
        return (n - 1) / ((newest - oldest) / 1e9);
    }

    /**
     * Swap in a fresh World (used by save/load and New Game). Clears any pending commands,
     * resets selection to {@link Selection#NONE}, and notifies listeners via
     * {@link EngineEvent.WorldReplaced} so panels can rebind their cached state.
     */
    public void reset(World newWorld) {
        EdtGuard.assertEdt();
        this.world = newWorld;
        simulator.clearCommands();
        lastLogged = newWorld.recentEvents.peekLast();
        tickCount = 0;
        LOG.info(() -> "World replaced: seed=" + newWorld.seed + " tick=" + newWorld.tick);
        if (!Selection.NONE.equals(selection)) {
            selection = Selection.NONE;
            fire(new EngineEvent.SelectionChanged(Selection.NONE));
        }
        fire(new EngineEvent.WorldReplaced(newWorld.tick));
    }

    public void setDebugEnabled(boolean on) {
        EdtGuard.assertEdt();
        if (on == debugEnabled) return;
        debugEnabled = on;
        LOG.info(() -> "Debug mode " + (on ? "on" : "off"));
        fire(new EngineEvent.DebugModeChanged(on));
    }

    public void setSelection(Selection s) {
        if (s.equals(selection)) return;
        selection = s;
        fire(new EngineEvent.SelectionChanged(s));
    }

    public void setSpeed(Speed s) {
        if (s == speed) return;
        speed = s;
        LOG.fine(() -> "Speed " + s);
        fire(new EngineEvent.SpeedChanged(s));
    }

    public void setView(EngineEvent.ViewChanged.View v) {
        if (v == view) return;
        view = v;
        fire(new EngineEvent.ViewChanged(v));
    }

    /**
     * Log every event newer than {@link #lastLogged}, oldest first. Keyed on identity, not
     * tick, because CommandPhase emits rejections with the pre-increment tick. If the marker
     * was evicted from the 200-event ring, the whole ring is logged.
     */
    private void logNewEvents() {
        var events = world.recentEvents;
        if (events.isEmpty() || events.peekLast() == lastLogged) return;
        List<Event> fresh = new ArrayList<>();
        for (Iterator<Event> it = events.descendingIterator(); it.hasNext(); ) {
            Event e = it.next();
            if (e == lastLogged) break;
            fresh.add(e);
        }
        for (int i = fresh.size() - 1; i >= 0; i--) {
            Event e = fresh.get(i);
            Level level = e.severity() == EventSeverity.INFO ? Level.INFO : Level.WARNING;
            if (EVENTS.isLoggable(level)) {
                EVENTS.log(level, String.format("[Y%d D%d] %s: %s",
                    e.tick() / 365, e.tick() % 365 + 1, e.kind(), e.message()));
            }
        }
        lastLogged = events.peekLast();
    }

    private void fire(EngineEvent e) {
        // Iterate over a snapshot so listeners that subscribe/unsubscribe during dispatch don't mutate the live list.
        for (EngineListener l : new ArrayList<>(listeners)) l.onEvent(e);
    }
}
