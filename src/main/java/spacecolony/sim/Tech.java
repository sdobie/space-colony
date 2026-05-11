package spacecolony.sim;

import java.util.List;

public record Tech(
    String id,
    String name,
    String description,
    long researchCost,
    List<String> prereqIds
) {}
