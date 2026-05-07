package spacecolony.sim;

import java.util.HashSet;
import java.util.Set;

public class TechState {
    public final Set<String> researched = new HashSet<>();
    public String activeId; // null when none
    public double accumulatedPoints; // toward activeId
}
