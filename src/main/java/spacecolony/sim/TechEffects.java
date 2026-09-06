package spacecolony.sim;

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
}
