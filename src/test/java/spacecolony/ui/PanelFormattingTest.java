package spacecolony.ui;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.BuildingType;
import spacecolony.sim.GoalCatalog;
import spacecolony.sim.PopCapBreakdown;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import static org.junit.jupiter.api.Assertions.*;

class PanelFormattingTest {

    @Test
    void techDelta_showsBeforeAfterAndPercent() {
        var d = new TechEffects.Delta("Ship fuel cost", 0.80, 0.56, false);
        assertEquals("Ship fuel cost ×0.80 → ×0.56 (−30%)", TechModal.formatDelta(d, false));
        assertEquals("Ship fuel cost ×0.80 → ×0.56 (−30%, applied)", TechModal.formatDelta(d, true));
    }

    @Test
    void techDelta_moraleCeilingIsAbsolute() {
        var d = new TechEffects.Delta("Morale ceiling", 1.0, 1.2, false);
        assertEquals("Morale ceiling 1.00 → 1.20 (+20%)", TechModal.formatDelta(d, false));
    }

    @Test
    void techDelta_flagReadsOnOff() {
        var d = new TechEffects.Delta("Gas-giant fuel mining", 0, 1, true);
        assertEquals("Gas-giant fuel mining: off → on", TechModal.formatDelta(d, false));
    }

    @Test
    void moraleLine_notesLifeSupportOnlyAboveBase() {
        assertEquals("Morale: 0.95 / 1.00", DetailPanel.moraleLine(0.95, 1.0));
        assertEquals("Morale: 1.10 / 1.56  (life support +56%)", DetailPanel.moraleLine(1.10, 1.56));
    }

    @Test
    void goalReward_hidesZeroRewards() {
        assertEquals("+5000 credits", GoalsModal.rewardText(GoalCatalog.get("first-mars-colony")));
        assertEquals("+1000 research", GoalsModal.rewardText(GoalCatalog.get("pop-1000")));
        assertEquals("+6000 credits  +200 research", GoalsModal.rewardText(GoalCatalog.get("belt-presence")));
    }

    @Test
    void eventLine_usesCalendarAndPrefixesOnlyRejections() {
        Event meteor = new Event(376, EventSeverity.WARNING, EventKind.METEOR_STRIKE, "Meteor strike on Mars", "mars", null, null);
        assertEquals("Y1 D12  Meteor strike on Mars", EventStripPanel.format(meteor));
        Event rejected = Event.warning(0, EventKind.COMMAND_REJECTED, "No such site: x");
        assertEquals("Y0 D1  Command rejected: No such site: x", EventStripPanel.format(rejected));
    }

    @Test void capBreakdown_omitsMultiplierAtOne() {
        assertEquals("  base 200 + habitats 100", DetailPanel.capBreakdown(new PopCapBreakdown(200, 100, 1.0, 300)));
        assertEquals("  base 200 + habitats 100 × 1.56",
            DetailPanel.capBreakdown(new PopCapBreakdown(200, 100, 1.56, 468)));
    }

    @Test void techNote_listsOnlyChangedMultipliers() {
        TechState t = new TechState();
        assertEquals("", DetailPanel.techNote(BuildingType.MINE, t));
        t.researched.add("basic-mining");
        t.researched.add("hydroponics");
        assertEquals("  ore ×1.10", DetailPanel.techNote(BuildingType.MINE, t));
        assertEquals("  food ×1.30 · water ×0.70", DetailPanel.techNote(BuildingType.FARM, t));
        assertEquals("", DetailPanel.techNote(BuildingType.SHIPYARD, t));
    }

    @Test void techRateAndEta() {
        assertEquals("  ·  1.3 pts/day", TechModal.rateText(1.25));
        assertEquals("  ·  no research labs", TechModal.rateText(0));
        assertEquals("250 / 800  ·  ETA 440 days", TechModal.progressText(250, 800, 1.25));
        assertEquals("250 / 800  ·  no labs", TechModal.progressText(250, 800, 0));
    }

    @Test void goalProgressText() {
        spacecolony.sim.World w = spacecolony.world.WorldGenerator.generate(1L);
        assertEquals("100 / 1,000", GoalsModal.progressText(GoalCatalog.get("pop-1000"), w));
        assertEquals("1 / 5 bodies", GoalsModal.progressText(GoalCatalog.get("five-bodies"), w));
        assertEquals("0 / 10 ships", GoalsModal.progressText(GoalCatalog.get("fleet-10"), w));
        assertEquals("not yet", GoalsModal.progressText(GoalCatalog.get("first-mars-colony"), w));
        w.goals.achieved.add("pop-1000");
        assertEquals("done", GoalsModal.progressText(GoalCatalog.get("pop-1000"), w));
    }
}
