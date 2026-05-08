package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.Command;

/**
 * Pure simulation engine. Advances {@link World} by one tick at a time. Player intent
 * enters via the command queue; UI never mutates World directly.
 *
 * Per-tick phases (run in this order):
 *   1. Drain command queue
 *   2. Advance tick + orbits (positions are computed from tick on demand)
 *   3. Advance ships in transit (arrivals)
 *   4. Per-site production & consumption
 *   5. Loading / unloading ships (departures)
 *   6. Random events
 *   7. Research progress
 *   8. Goal check
 */
public class Simulator {
    private final Deque<Command> commandQueue = new ArrayDeque<>();

    public void enqueue(Command c) { commandQueue.addLast(c); }

    public void advance(World w) {
        drainCommands(w);
        w.tick++;
        advanceTransits(w);
        productionAndConsumption(w);
        loadingAndUnloading(w);
        randomEvents(w);
        researchProgress(w);
        goalCheck(w);
    }

    private void drainCommands(World w)         { /* Task 20 */ }
    private void advanceTransits(World w)       { /* Task 21 */ }
    private void productionAndConsumption(World w) { /* Task 22 */ }
    private void loadingAndUnloading(World w)   { /* Task 21 */ }
    private void randomEvents(World w)          { /* Task 23 */ }
    private void researchProgress(World w)      { /* Task 24 */ }
    private void goalCheck(World w)             { /* Task 25 */ }
}
