package spacecolony.sim;

import java.util.function.Predicate;

/**
 * A milestone the player can achieve. Predicate evaluated each tick against the World.
 * Achieved goals stay achieved; goals never end the game.
 */
public record Goal(
    String id,
    String name,
    String description,
    long creditReward,
    long researchReward,
    Predicate<World> predicate
) {}
