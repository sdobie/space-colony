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
 * Per-tick phases ({@link SimPhase}):
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
    private final Deque<Command> commandQueue = new ArrayDeque<>();
    private PhaseObserver observer; // null = no timing

    public void enqueue(Command c) { commandQueue.addLast(c); }

    /** Drop all pending commands. Used by {@link spacecolony.engine.Engine#reset} on world swap. */
    public void clearCommands() { commandQueue.clear(); }

    /** Commands enqueued but not yet drained by the next {@link #advance}. */
    public int queueDepth() { return commandQueue.size(); }

    /** Install (or clear, with null) the debug phase-timing hook. */
    public void setPhaseObserver(PhaseObserver o) { this.observer = o; }

    public void advance(World w) {
        if (observer == null) {
            CommandPhase.drain(w, commandQueue);
            w.tick++;
            TransitPhase.advanceTransits(w);
            ProductionPhase.run(w);
            TransitPhase.loadingAndUnloading(w);
            EventPhase.run(w);
            ResearchPhase.run(w);
            GoalPhase.run(w);
            return;
        }
        long next = w.tick + 1;
        timed(next, SimPhase.COMMANDS,   () -> CommandPhase.drain(w, commandQueue));
        timed(next, SimPhase.TICK,       () -> w.tick++);
        timed(next, SimPhase.ARRIVALS,   () -> TransitPhase.advanceTransits(w));
        timed(next, SimPhase.PRODUCTION, () -> ProductionPhase.run(w));
        timed(next, SimPhase.DEPARTURES, () -> TransitPhase.loadingAndUnloading(w));
        timed(next, SimPhase.EVENTS,     () -> EventPhase.run(w));
        timed(next, SimPhase.RESEARCH,   () -> ResearchPhase.run(w));
        timed(next, SimPhase.GOALS,      () -> GoalPhase.run(w));
    }

    private void timed(long tick, SimPhase p, Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        observer.phaseDone(tick, p, System.nanoTime() - t0);
    }
}
