package spacecolony.sim;

/** Optional timing hook for {@link Simulator}. {@code tick} is the tick being produced (post-increment). */
@FunctionalInterface
public interface PhaseObserver {
    void phaseDone(long tick, SimPhase phase, long nanos);
}
