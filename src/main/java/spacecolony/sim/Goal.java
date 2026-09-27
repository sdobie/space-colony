package spacecolony.sim;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * A milestone the player can achieve. Predicate evaluated each tick against the World.
 * Achieved goals stay achieved; goals never end the game.
 */
public record Goal(
    String id,
    String name,
    String description,
    GoalCategory category,
    long creditReward,
    long researchReward,
    Predicate<World> predicate,
    /** Raw measure toward the goal in [0, 1] (clamped by displayProgress). Display only. */
    ToDoubleFunction<World> progress
) {
    /** 1.0 once achieved; otherwise the live measure clamped to [0, 1]. */
    public double displayProgress(World w) {
        if (w.goals.achieved.contains(id)) return 1.0;
        return Math.max(0.0, Math.min(1.0, progress.applyAsDouble(w)));
    }
}
