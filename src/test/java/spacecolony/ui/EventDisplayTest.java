package spacecolony.ui;

import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Site;
import spacecolony.sim.TechState;
import spacecolony.sim.economy.DayReport;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 11 §5: how events and their damage read in the strip and the colony panel. */
class EventDisplayTest {
    private static Event meteor(String msg) {
        return new Event(39, EventSeverity.ERROR, EventKind.METEOR_STRIKE, msg, "mars", "site-mars", null);
    }

    @Test void consequencesSplitAfterTheColon() {
        Event e = meteor("Major meteor strike at Mars Base: Farm L2 and Mine L1 damaged; 38 ICE and 12 METAL destroyed; "
            + "3 colonists died; morale −10%; impact dust: farms at 75% for 20 days");
        assertEquals(List.of("Farm L2 and Mine L1 damaged", "38 ICE and 12 METAL destroyed", "3 colonists died",
            "morale −10%", "impact dust: farms at 75% for 20 days"), EventStripPanel.consequences(e));
    }

    @Test void repairCostCommasStayInOneLine() {
        Event e = meteor("Meteor strike at Mars Base: Farm L2 damaged (repair 4 METAL, 1 COMPONENTS); 19 ICE destroyed");
        assertEquals(List.of("Farm L2 damaged (repair 4 METAL, 1 COMPONENTS)", "19 ICE destroyed"),
            EventStripPanel.consequences(e));
    }

    @Test void tooltipListsEachConsequenceAndARepairHint() {
        String t = EventStripPanel.tooltip(meteor("Meteor strike at Mars Base: Farm L2 damaged (repair 4 METAL, 1 COMPONENTS); 19 ICE destroyed"), true);
        assertTrue(t.startsWith("<html><b>Meteor strike at Mars Base</b> · Y0 D40"), t);
        assertTrue(t.contains("<br>• Farm L2 damaged (repair 4 METAL, 1 COMPONENTS)<br>• 19 ICE destroyed"), t);
        assertTrue(t.contains("Repair damaged buildings"), t);
        assertTrue(t.endsWith("<i>Click to select</i></html>"), t);
    }

    @Test void otherEventsGetNoConsequenceLines() {
        Event e = new Event(0, EventSeverity.INFO, EventKind.GOAL_ACHIEVED, "Goal achieved: Fleet: 10", null, null, null);
        assertEquals(List.of(), EventStripPanel.consequences(e));
        assertEquals("<html><b>Goal achieved</b> · Y0 D1</html>", EventStripPanel.tooltip(e, false));
    }

    @Test void damagedRowSaysWhatAndWhen() {
        Building farm = new Building(BuildingType.FARM, 2);
        farm.damage(EventKind.METEOR_STRIKE, 365 + 39);
        assertEquals("  Farm L2   damaged by meteor strike, Y1 D40: repair 4 METAL, 2 COMPONENTS",
            DetailPanel.buildingText(farm, null, new DayReport(), new TechState()));
    }

    @Test void damagedRowWithoutCauseKeepsTheOldText() {
        Building farm = new Building(BuildingType.FARM, 1);
        farm.enabled = false;
        assertEquals("  Farm L1   damaged: repair 4 METAL, 2 COMPONENTS",
            DetailPanel.buildingText(farm, null, new DayReport(), new TechState()));
    }

    @Test void offlinePowerPlantRow() {
        Building plant = new Building(BuildingType.POWER_PLANT, 1);
        plant.offlineDays = 2;
        assertEquals("  Power plant L1   offline after solar flare, back in 2 days",
            DetailPanel.buildingText(plant, null, new DayReport(), new TechState()));
        plant.offlineDays = 1;
        assertTrue(DetailPanel.buildingText(plant, null, new DayReport(), new TechState()).endsWith("back in 1 day"));
    }

    @Test void dustLine() {
        Site s = new Site("x", "X", "mars", 0, 0, 100);
        assertNull(DetailPanel.dustLine(s));
        s.dustDays = 14;
        s.dustFactor = 0.75;
        assertEquals("Impact dust: farms at 75% for 14 more days", DetailPanel.dustLine(s));
        s.dustDays = 1;
        assertEquals("Impact dust: farms at 75% for 1 more day", DetailPanel.dustLine(s));
    }
}
