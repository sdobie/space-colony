package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * What one event did, in order, and the event text built from it (Plan 11 §5.1):
 * "Major meteor strike at Mars Base: Farm L2 and Mine L1 damaged; 38 ICE and 12 METAL destroyed;
 * 3 colonists died; morale −10%; impact dust: farms at 75% for 20 days".
 */
public final class EventEffects {
    /** Separates consequences in a message; a repair cost already uses commas. */
    public static final String SEPARATOR = "; ";

    private final List<Building> damaged = new ArrayList<>();
    private String damageVerb = "damaged";
    private final List<String> destroyed = new ArrayList<>();
    private int died;
    private double moraleDrop;
    private double dustFactor = 1.0;
    private int dustDays;
    private final List<String> offline = new ArrayList<>();

    public void damaged(Building b, String verb) { damaged.add(b); damageVerb = verb; }
    public void destroyed(Resource r, double amount) { destroyed.add(whole(amount) + " " + r); }
    public void died(int colonists) { died += colonists; }
    public void morale(double drop) { moraleDrop += drop; }
    public void dust(double factor, int days) { dustFactor = factor; dustDays = days; }
    public void offline(Site s, int days) { offline.add(s.name + " for " + days + (days == 1 ? " day" : " days")); }

    public boolean isEmpty() {
        return damaged.isEmpty() && destroyed.isEmpty() && died == 0 && moraleDrop <= 0
            && dustDays == 0 && offline.isEmpty();
    }

    public boolean anyDeaths() { return died > 0; }
    public boolean anyDamage() { return !damaged.isEmpty(); }

    /** Each consequence as its own line, in the order the message lists them. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        if (!damaged.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Building b : damaged) names.add(BuildingCatalog.displayName(b.type) + " L" + b.level);
            String line = joinAnd(names) + " " + damageVerb;
            if (damaged.size() == 1)
                line += " (repair " + BuildCost.describe(Construction.repairCost(damaged.get(0).type).resources()) + ")";
            out.add(line);
        }
        if (!destroyed.isEmpty()) out.add(joinAnd(destroyed) + " destroyed");
        if (died > 0) out.add(died + (died == 1 ? " colonist died" : " colonists died"));
        if (moraleDrop > 0) out.add(String.format("morale −%.0f%%", moraleDrop * 100));
        if (dustDays > 0) out.add(String.format("impact dust: farms at %.0f%% for %d days", dustFactor * 100, dustDays));
        if (!offline.isEmpty()) out.add("power plants offline at " + String.join(", ", offline));
        return out;
    }

    /**
     * "{title} at {site}: {lines}" or, with no site, "{title} on {body}: …" / "{title}: …";
     * "no damage" when nothing happened.
     */
    public String message(String title, Site site, Body body) {
        String where = site != null ? " at " + site.name : body != null ? " on " + body.name : "";
        String what = isEmpty() ? "no damage" : String.join(SEPARATOR, lines());
        return title + where + ": " + what;
    }

    /** "A", "A and B", "A, B and C". */
    static String joinAnd(List<String> parts) {
        if (parts.size() <= 1) return String.join("", parts);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static String whole(double v) { return String.format("%.0f", Math.floor(v + 1e-9)); }
}
