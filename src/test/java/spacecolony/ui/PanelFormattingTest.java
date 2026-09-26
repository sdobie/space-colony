package spacecolony.ui;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.GoalCatalog;
import spacecolony.sim.TechEffects;
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
}
