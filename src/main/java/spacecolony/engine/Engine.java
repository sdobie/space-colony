package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.sim.commands.Command;

/**
 * Top-level engine. Wraps a World + Simulator and notifies UI listeners on changes.
 * UI calls {@link #enqueue} to submit player commands; the next {@link #tick} drains them.
 *
 * Not thread-safe — meant to be driven from the EDT.
 */
public class Engine {
    private static final Logger LOG = Logger.getLogger(Engine.class.getName());

    private World world;
    private final Simulator simulator = new Simulator();
    private final List<EngineListener> listeners = new ArrayList<>();
    private Selection selection = Selection.NONE;
    private Speed speed = Speed.PAUSED;
    private EngineEvent.ViewChanged.View view = EngineEvent.ViewChanged.View.SYSTEM_MAP;
    private boolean debugEnabled;

    public Engine(World world) { this.world = world; }

    public World world() { return world; }
    public Selection selection() { return selection; }
    public Speed speed() { return speed; }
    public EngineEvent.ViewChanged.View view() { return view; }
    public boolean debugEnabled() { return debugEnabled; }
    /** Commands enqueued but not yet drained by the next tick. */
    public int pendingCommandCount() { return simulator.pendingCommandCount(); }

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
        simulator.enqueue(c);
    }

    /** Advance the simulator one tick, then fire WorldChanged. */
    public void tick() {
        EdtGuard.assertEdt();
        simulator.advance(world);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /**
     * Advance {@code n} ticks back-to-back and fire a single WorldChanged at the end, so
     * panels repaint once instead of n times (debug "Run N ticks").
     */
    public void advanceTicks(int n) {
        EdtGuard.assertEdt();
        if (n <= 0) throw new IllegalArgumentException("n must be positive, was " + n);
        for (int i = 0; i < n; i++) simulator.advance(world);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /**
     * Debug-only direct mutation of the World (forced events, inspector edits). The only path
     * by which anything outside the simulator mutates World. Allowed only while paused so
     * edits never interleave with ticks; logs {@code description} at INFO so a changed world
     * can be traced to the edit that changed it. Fires WorldChanged afterwards.
     */
    public void applyDebugEdit(String description, Consumer<World> edit) {
        EdtGuard.assertEdt();
        if (!speed.isPaused()) throw new IllegalStateException("Pause the game before editing the world");
        edit.accept(world);
        LOG.info(() -> "Debug edit: " + description);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /** Install the simulator's phase-timing observer (null clears it). */
    public void setPhaseObserver(Simulator.PhaseObserver o) {
        EdtGuard.assertEdt();
        simulator.setPhaseObserver(o);
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
        if (!Selection.NONE.equals(selection)) {
            selection = Selection.NONE;
            fire(new EngineEvent.SelectionChanged(Selection.NONE));
        }
        LOG.info(() -> "World replaced (seed " + newWorld.seed + ", tick " + newWorld.tick + ")");
        fire(new EngineEvent.WorldReplaced(newWorld.tick));
    }

    public void setDebugEnabled(boolean on) {
        EdtGuard.assertEdt();
        if (on == debugEnabled) return;
        debugEnabled = on;
        LOG.info(() -> "Debug mode " + (on ? "enabled" : "disabled"));
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
        fire(new EngineEvent.SpeedChanged(s));
    }

    public void setView(EngineEvent.ViewChanged.View v) {
        if (v == view) return;
        view = v;
        fire(new EngineEvent.ViewChanged(v));
    }

    private void fire(EngineEvent e) {
        // Iterate over a snapshot so listeners that subscribe/unsubscribe during dispatch don't mutate the live list.
        for (EngineListener l : new ArrayList<>(listeners)) l.onEvent(e);
    }
}
