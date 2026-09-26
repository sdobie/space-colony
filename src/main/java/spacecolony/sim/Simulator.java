package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.Command;
import spacecolony.sim.phases.CommandPhase;
import spacecolony.sim.phases.EventPhase;
import spacecolony.sim.phases.GoalPhase;
import spacecolony.sim.phases.ProductionPhase;
import spacecolony.sim.phases.ResearchPhase;
import spacecolony.sim.phases.TransitPhase;

/**
 * Tick-by-tick simulation orchestrator. Each {@link #advance(World)} call runs the
 * 8 phases in order. Player intent enters via {@link #enqueue(Command)}; UI never
 * mutates World directly.
 *
 * Per-tick phases:
 *   1. Drain command queue                  (CommandPhase)
 *   2. Advance tick (here)
 *   3. Advance ships in transit (arrivals)  (TransitPhase.advanceTransits)
 *   4. Per-site production & consumption    (ProductionPhase)
 *   5. Loading/unloading ships (departures) (TransitPhase.loadingAndUnloading)
 *   6. Random events                        (EventPhase)
 *   7. Research progress                    (ResearchPhase)
 *   8. Goal check                           (GoalPhase)
 *
 * Single-threaded. {@code enqueue} is not thread-safe — call from the same thread
 * that calls {@link #advance}.
 */
public class Simulator {
    /** Names of the timed phases, in the order {@link PhaseObserver#onTick} reports them. */
    public static final String[] PHASE_NAMES = {
        "commands", "transits", "production", "loading", "events", "research", "goals"
    };

    /** Debug hook: receives per-phase wall-clock nanos after each tick. */
    @FunctionalInterface
    public interface PhaseObserver {
        void onTick(long tick, long[] phaseNanos);
    }

    private final Deque<Command> commandQueue = new ArrayDeque<>();
    private PhaseObserver phaseObserver;

    public void enqueue(Command c) { commandQueue.addLast(c); }

    public int pendingCommandCount() { return commandQueue.size(); }

    /** Install (or clear, with null) the phase-timing observer. Timing is skipped when null. */
    public void setPhaseObserver(PhaseObserver o) { this.phaseObserver = o; }

    /** Drop all pending commands. Used by {@link spacecolony.engine.Engine#reset} on world swap. */
    public void clearCommands() { commandQueue.clear(); }

    public void advance(World w) {
        PhaseObserver obs = phaseObserver;
        long[] ns = obs == null ? null : new long[PHASE_NAMES.length];
        long t = obs == null ? 0 : System.nanoTime();
        CommandPhase.drain(w, commandQueue);           t = lap(ns, 0, t);
        w.tick++;
        TransitPhase.advanceTransits(w);               t = lap(ns, 1, t);
        ProductionPhase.run(w);                        t = lap(ns, 2, t);
        TransitPhase.loadingAndUnloading(w);           t = lap(ns, 3, t);
        EventPhase.run(w);                             t = lap(ns, 4, t);
        ResearchPhase.run(w);                          t = lap(ns, 5, t);
        GoalPhase.run(w);                                  lap(ns, 6, t);
        if (obs != null) obs.onTick(w.tick, ns);
    }

    private static long lap(long[] ns, int phase, long start) {
        if (ns == null) return 0;
        long now = System.nanoTime();
        ns[phase] = now - start;
        return now;
    }
}
