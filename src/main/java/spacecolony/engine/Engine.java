package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
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
    private World world;
    private final Simulator simulator = new Simulator();
    private final List<EngineListener> listeners = new ArrayList<>();
    private Selection selection = Selection.NONE;
    private Speed speed = Speed.PAUSED;
    private EngineEvent.ViewChanged.View view = EngineEvent.ViewChanged.View.SYSTEM_MAP;

    public Engine(World world) { this.world = world; }

    public World world() { return world; }
    public Selection selection() { return selection; }
    public Speed speed() { return speed; }
    public EngineEvent.ViewChanged.View view() { return view; }

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
        fire(new EngineEvent.WorldReplaced(newWorld.tick));
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
