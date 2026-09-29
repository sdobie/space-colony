package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.sim.BuildingType.*;

class ConstructionTest {
    private static Site site(double metal, double comps) {
        Site s = new Site("s", "Testville", "b", 0, 0, 100);
        s.stockpile.put(Resource.METAL, metal);
        s.stockpile.put(Resource.COMPONENTS, comps);
        return s;
    }

    private static Map<Resource, Double> mc(double metal, double comps) {
        return Map.of(Resource.METAL, metal, Resource.COMPONENTS, comps);
    }

    @Test void slotsCountBuildingsUnderConstruction() {
        Site s = site(0, 0);
        s.addBuilding(new Building(FARM, 1));
        s.addBuilding(new Building(MINE, 0)).daysLeft = 3;
        assertEquals(2, Construction.slotsUsed(s));
    }

    @Test void upgradeCostsGrowWithLevel() {
        assertEquals(mc(15, 5), Construction.upgradeCost(FARM, 2).resources());
        assertEquals(mc(23, 8), Construction.upgradeCost(FARM, 3).resources());
        assertEquals(mc(30, 10), Construction.upgradeCost(FARM, 4).resources());
        assertEquals(mc(38, 13), Construction.upgradeCost(FARM, 5).resources());
        assertEquals(3, Construction.upgradeCost(FARM, 5).days());
    }

    @Test void investedAndDemolishRefund() {
        Building farm = new Building(FARM, 3);
        assertEquals(mc(53, 18), Construction.invested(farm));
        assertEquals(mc(26, 9), Construction.demolishRefund(farm));
    }

    @Test void cancelRefundsTheStepInFull() {
        Building fresh = new Building(FARM, 0);
        fresh.daysLeft = 2;
        assertEquals(mc(15, 5), Construction.cancelRefund(fresh));
        Building upgrading = new Building(FARM, 2);
        upgrading.daysLeft = 1;
        assertEquals(mc(23, 8), Construction.cancelRefund(upgrading));
        assertTrue(Construction.cancelRefund(new Building(FARM, 2)).isEmpty());
    }

    @Test void repairIsAQuarter() {
        assertEquals(mc(5, 3), Construction.repairCost(RESEARCH_LAB).resources());
        assertEquals(0, Construction.repairCost(RESEARCH_LAB).days());
    }

    @Test void whyNotBuild() {
        Site full = site(1000, 1000);
        for (int i = 0; i < Construction.SLOTS; i++) full.addBuilding(new Building(FARM, 1));
        assertEquals("No free building slot (10 of 10)", Construction.whyNotBuild(full, MINE));
        assertEquals("Need 12 more METAL (have 8 of 20)", Construction.whyNotBuild(site(8, 50), MINE));
        assertEquals("Need 12 more METAL (have 8 of 20) and 3 more COMPONENTS",
            Construction.whyNotBuild(site(8, 2), MINE));
        assertNull(Construction.whyNotBuild(site(20, 5), MINE));
    }

    @Test void whyNotUpgrade() {
        Site s = site(1000, 1000);
        assertEquals("Already level 5", Construction.whyNotUpgrade(s, new Building(FARM, 5)));
        Building building = new Building(FARM, 0);
        building.daysLeft = 2;
        assertEquals("Under construction", Construction.whyNotUpgrade(s, building));
        Building damaged = new Building(FARM, 1);
        damaged.enabled = false;
        assertEquals("Damaged: repair it first", Construction.whyNotUpgrade(s, damaged));
        assertNull(Construction.whyNotUpgrade(s, new Building(FARM, 1)));
        assertEquals("Need 15 more METAL (have 0 of 15) and 5 more COMPONENTS",
            Construction.whyNotUpgrade(site(0, 0), new Building(FARM, 1)));
    }

    @Test void whyNotRepairAndDemolish() {
        Site s = site(1000, 1000);
        assertEquals("Not damaged", Construction.whyNotRepair(s, new Building(FARM, 1)));
        Building plant = new Building(POWER_PLANT, 1);
        plant.enabled = false;
        assertEquals("Power plants come back on their own the next day", Construction.whyNotRepair(s, plant));
        Building damaged = new Building(FARM, 1);
        damaged.enabled = false;
        assertNull(Construction.whyNotRepair(s, damaged));
        Building building = new Building(FARM, 0);
        building.daysLeft = 1;
        assertEquals("Under construction: cancel it instead", Construction.whyNotDemolish(building));
        assertNull(Construction.whyNotDemolish(new Building(FARM, 1)));
    }
}
