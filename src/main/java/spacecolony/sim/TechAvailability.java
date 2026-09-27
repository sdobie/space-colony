package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;

/** Prerequisite checks shared by CommandPhase (validation) and the tech modal (display). */
public final class TechAvailability {
    private TechAvailability() {}

    public static boolean prereqsMet(TechState s, Tech t) {
        return s.researched.containsAll(t.prereqIds());
    }

    /** Unresearched prereq ids, in the order the tech declares them. */
    public static List<String> missingPrereqs(TechState s, Tech t) {
        List<String> out = new ArrayList<>();
        for (String id : t.prereqIds()) if (!s.researched.contains(id)) out.add(id);
        return out;
    }

    /** Longest prereq chain below {@code t}: 0 for techs with no prereqs. */
    public static int tier(Tech t) {
        int best = 0;
        for (String id : t.prereqIds()) best = Math.max(best, 1 + tier(TechCatalog.get(id)));
        return best;
    }
}
