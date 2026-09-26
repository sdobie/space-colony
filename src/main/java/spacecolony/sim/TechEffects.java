package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Static multipliers consulted by each sim phase to apply tech-tree effects.
 * Plan 4 wires all 17 v1 techs (see TechCatalog) into sim behaviour here.
 *
 * Each tech composes multiplicatively. With no techs researched, every multiplier
 * returns 1.0 (or false for booleans). Methods are intentionally explicit — no
 * reflection, no table — so a diff against TechCatalog is grep-friendly.
 */
public final class TechEffects {
    private TechEffects() {}

    // === ProductionPhase ===

    public static double mineOreMultiplier(TechState t) {
        return (t.researched.contains("basic-mining") ? 1.10 : 1.0)
             * (t.researched.contains("auto-mining")  ? 1.30 : 1.0);
    }

    public static double mineSilicateMultiplier(TechState t) {
        return (t.researched.contains("auto-mining") ? 1.30 : 1.0);
    }

    public static double farmFoodMultiplier(TechState t) {
        return (t.researched.contains("basic-farming") ? 1.10 : 1.0)
             * (t.researched.contains("hydroponics")   ? 1.30 : 1.0);
    }

    public static double farmWaterDemandMultiplier(TechState t) {
        return (t.researched.contains("hydroponics") ? 0.70 : 1.0);
    }

    public static double powerPlantMultiplier(TechState t) {
        return (t.researched.contains("solar-panels") ? 1.25 : 1.0);
    }

    public static double refineryMultiplier(TechState t) {
        return (t.researched.contains("smelting") ? 1.20 : 1.0);
    }

    public static double moraleCeiling(TechState t) {
        return (t.researched.contains("life-support-i")  ? 1.20 : 1.0)
             * (t.researched.contains("life-support-ii") ? 1.30 : 1.0);
    }

    // === TransitPhase ===

    public static double fuelCostMultiplier(TechState t) {
        return (t.researched.contains("ion-drives")    ? 0.80 : 1.0)
             * (t.researched.contains("fusion-drives") ? 0.70 : 1.0)
             * (t.researched.contains("antimatter")    ? 0.50 : 1.0);
    }

    public static boolean gasGiantFuelEnabled(TechState t) {
        return t.researched.contains("atm-mining");
    }

    // === EventPhase ===

    public static double diseaseSeverityMultiplier(TechState t) {
        return (t.researched.contains("medicine") ? 0.50 : 1.0);
    }

    // === ResearchPhase ===

    public static double researchLabMultiplier(TechState t) {
        return (t.researched.contains("research-i")  ? 1.25 : 1.0)
             * (t.researched.contains("research-ii") ? 1.25 : 1.0);
    }

    // === HABITAT cap (ProductionPhase) ===

    public static double popCapMultiplier(TechState t) {
        return (t.researched.contains("colony-mgmt-i")  ? 1.20 : 1.0)
             * (t.researched.contains("colony-mgmt-ii") ? 1.30 : 1.0);
    }

    // === UI: per-tech deltas ===

    /**
     * One multiplier that a tech moves. {@code before} is the value without the tech,
     * {@code after} the value with it, both given the rest of the researched set.
     * Flags (e.g. gas-giant fuel) use 0.0 / 1.0 and set {@code flag}.
     */
    public record Delta(String label, double before, double after, boolean flag) {}

    private record Effect(String label, ToDoubleFunction<TechState> value, boolean flag) {}

    private static final List<Effect> EFFECTS = List.of(
        new Effect("Mine ore output",       TechEffects::mineOreMultiplier,          false),
        new Effect("Mine silicate output",  TechEffects::mineSilicateMultiplier,     false),
        new Effect("Farm food output",      TechEffects::farmFoodMultiplier,         false),
        new Effect("Farm water use",        TechEffects::farmWaterDemandMultiplier,  false),
        new Effect("Power plant output",    TechEffects::powerPlantMultiplier,       false),
        new Effect("Refinery output",       TechEffects::refineryMultiplier,         false),
        new Effect("Morale ceiling",        TechEffects::moraleCeiling,              false),
        new Effect("Ship fuel cost",        TechEffects::fuelCostMultiplier,         false),
        new Effect("Gas-giant fuel mining", t -> gasGiantFuelEnabled(t) ? 1.0 : 0.0, true),
        new Effect("Disease severity",      TechEffects::diseaseSeverityMultiplier,  false),
        new Effect("Research lab output",   TechEffects::researchLabMultiplier,      false),
        new Effect("Population cap",        TechEffects::popCapMultiplier,           false)
    );

    /**
     * What researching {@code techId} changes, measured against the current researched
     * set: for an unresearched tech, today's value vs. with it; for a researched tech,
     * the value it contributes (without it vs. today). Empty for unknown ids.
     */
    public static List<Delta> deltas(TechState current, String techId) {
        TechState without = copyWith(current, s -> !s.equals(techId));
        TechState with = copyWith(current, s -> true);
        with.researched.add(techId);
        List<Delta> out = new ArrayList<>();
        for (Effect e : EFFECTS) {
            double b = e.value().applyAsDouble(without);
            double a = e.value().applyAsDouble(with);
            if (Math.abs(a - b) > 1e-9) out.add(new Delta(e.label(), b, a, e.flag()));
        }
        return out;
    }

    private static TechState copyWith(TechState src, Predicate<String> keep) {
        TechState t = new TechState();
        for (String id : src.researched) if (keep.test(id)) t.researched.add(id);
        return t;
    }
}
