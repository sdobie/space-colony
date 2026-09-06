package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechEffectsTest {
    private static final double EPS = 1e-9;

    private static TechState withTechs(String... ids) {
        TechState t = new TechState();
        for (String id : ids) t.researched.add(id);
        return t;
    }

    // === No techs: everything is 1.0 (or false for booleans). ===

    @Test
    void noTechs_allMultipliersAreOne() {
        TechState t = new TechState();
        assertEquals(1.0, TechEffects.mineOreMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.mineSilicateMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.farmFoodMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.farmWaterDemandMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.powerPlantMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.refineryMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.moraleCeiling(t), EPS);
        assertEquals(1.0, TechEffects.fuelCostMultiplier(t), EPS);
        assertFalse(TechEffects.gasGiantFuelEnabled(t));
        assertEquals(1.0, TechEffects.diseaseSeverityMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.researchLabMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.popCapMultiplier(t), EPS);
    }

    // === Per-tech effects (verbatim from TechCatalog descriptions). ===

    @Test void basicMining()    { assertEquals(1.10, TechEffects.mineOreMultiplier(withTechs("basic-mining")), EPS); }
    @Test void basicFarming()   { assertEquals(1.10, TechEffects.farmFoodMultiplier(withTechs("basic-farming")), EPS); }
    @Test void solarPanels()    { assertEquals(1.25, TechEffects.powerPlantMultiplier(withTechs("solar-panels")), EPS); }
    @Test void ionDrives()      { assertEquals(0.80, TechEffects.fuelCostMultiplier(withTechs("ion-drives")), EPS); }
    @Test void fusionDrives()   { assertEquals(0.70, TechEffects.fuelCostMultiplier(withTechs("fusion-drives")), EPS); }
    @Test void atmMining()      { assertTrue(TechEffects.gasGiantFuelEnabled(withTechs("atm-mining"))); }
    @Test void hydroponicsFood()  { assertEquals(1.30, TechEffects.farmFoodMultiplier(withTechs("hydroponics")), EPS); }
    @Test void hydroponicsWater() { assertEquals(0.70, TechEffects.farmWaterDemandMultiplier(withTechs("hydroponics")), EPS); }
    @Test void smelting()       { assertEquals(1.20, TechEffects.refineryMultiplier(withTechs("smelting")), EPS); }
    @Test void medicine()       { assertEquals(0.50, TechEffects.diseaseSeverityMultiplier(withTechs("medicine")), EPS); }
    @Test void colonyMgmtI()    { assertEquals(1.20, TechEffects.popCapMultiplier(withTechs("colony-mgmt-i")), EPS); }
    @Test void researchI()      { assertEquals(1.25, TechEffects.researchLabMultiplier(withTechs("research-i")), EPS); }
    @Test void researchII()     { assertEquals(1.25, TechEffects.researchLabMultiplier(withTechs("research-ii")), EPS); }
    @Test void autoMiningOre()      { assertEquals(1.30, TechEffects.mineOreMultiplier(withTechs("auto-mining")), EPS); }
    @Test void autoMiningSilicate() { assertEquals(1.30, TechEffects.mineSilicateMultiplier(withTechs("auto-mining")), EPS); }
    @Test void colonyMgmtII()   { assertEquals(1.30, TechEffects.popCapMultiplier(withTechs("colony-mgmt-ii")), EPS); }
    @Test void lifeSupportI()   { assertEquals(1.20, TechEffects.moraleCeiling(withTechs("life-support-i")), EPS); }
    @Test void lifeSupportII()  { assertEquals(1.30, TechEffects.moraleCeiling(withTechs("life-support-ii")), EPS); }
    @Test void antimatter()     { assertEquals(0.50, TechEffects.fuelCostMultiplier(withTechs("antimatter")), EPS); }

    // === Stacking semantics: multiplicative composition. ===

    @Test
    void miningTechs_stackMultiplicatively() {
        assertEquals(1.10 * 1.30, TechEffects.mineOreMultiplier(withTechs("basic-mining", "auto-mining")), EPS);
        assertEquals(1.30, TechEffects.mineSilicateMultiplier(withTechs("basic-mining", "auto-mining")), EPS);
    }

    @Test
    void allDriveTechs_stackTo28pct() {
        assertEquals(0.80 * 0.70 * 0.50,
            TechEffects.fuelCostMultiplier(withTechs("ion-drives", "fusion-drives", "antimatter")), EPS);
    }

    @Test
    void bothColonyMgmt_stackTo156() {
        assertEquals(1.20 * 1.30,
            TechEffects.popCapMultiplier(withTechs("colony-mgmt-i", "colony-mgmt-ii")), EPS);
    }

    @Test
    void bothResearchMethods_stackTo15625() {
        assertEquals(1.25 * 1.25,
            TechEffects.researchLabMultiplier(withTechs("research-i", "research-ii")), EPS);
    }

    @Test
    void bothLifeSupports_stackTo156() {
        assertEquals(1.20 * 1.30,
            TechEffects.moraleCeiling(withTechs("life-support-i", "life-support-ii")), EPS);
    }
}
