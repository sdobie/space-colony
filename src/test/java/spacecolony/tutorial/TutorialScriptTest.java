package spacecolony.tutorial;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.ShipClass;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.testutil.Edt;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Walks the whole tutorial headless, doing what the UI would do at each step. Doubles as the
 * end-to-end test for Plan 6 Part A (fuel draw, colonizer to an unsettled body, founding).
 */
class TutorialScriptTest {
    /** Every bold label in the card text; TutorialTargetsTest checks these against the real UI. */
    static final Set<String> LABELS = Set.of("Earth Hub", "1×", "16×", "Build building...", "Research lab", "Build",
        "Tech", "Build ship...", "COLONIZER", "Dispatch...", "Mars (unsettled)", "Found colony…", "Goals",
        "Keep playing", "Main menu");

    private static TutorialContext ctx(Engine e) {
        return new TutorialContext(e.world(), e.speed(), e.selection(), e.view());
    }

    @Test void scenario_hasEventsOff_andFuelForTheFlight() {
        World w = TutorialScenario.world();
        assertFalse(w.randomEventsEnabled);
        assertEquals(400.0, w.findSite("site-earth-hub").stockpile.get(Resource.FUEL));
        assertEquals(TutorialScenario.TUTORIAL_SEED, w.seed);
    }

    @Test void fullWalkthrough() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(TutorialScenario.world());
            TutorialProgress p = new TutorialProgress(TutorialScript.steps());
            assertEquals(12, p.size());
            assertEquals("welcome", p.current().id());
            p.next();
            assertStep(p, e, "start-clock");

            e.setSpeed(Speed.X1);
            assertStep(p, e, "select-hub");
            e.setSelection(Selection.site("site-earth-hub"));
            assertStep(p, e, "read-dock");
            p.next();
            assertStep(p, e, "build-lab");

            e.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
            e.tick();
            assertStep(p, e, "research");
            e.enqueue(new QueueResearchCommand("basic-mining"));
            e.tick();
            assertStep(p, e, "build-colonizer");
            e.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
            e.tick();
            assertStep(p, e, "dispatch");
            e.setSelection(Selection.ship("c1"));
            e.enqueue(DispatchShipCommand.toBody("c1", "mars",
                Map.of(Resource.FOOD, 40.0, Resource.WATER, 40.0, Resource.METAL, 20.0)));
            e.tick();
            assertStep(p, e, "fast-forward");
            e.setSpeed(Speed.X16);
            for (int i = 0; i < 30 && e.world().findShip("c1").orbitingBodyId == null; i++) e.tick();
            assertStep(p, e, "found-colony");
            e.enqueue(new BuildSiteCommand("site-mars-a", "Ares", "mars", 0.2, 1.0, "c1"));
            e.tick();
            e.tick();
            assertStep(p, e, "goals");
            assertTrue(e.world().goals.achieved.contains("first-mars-colony"));
            assertEquals(20.0, e.world().findSite("site-mars-a").stockpile.get(Resource.METAL), 1e-6, "colonizer cargo moves into the colony");
            p.next();
            assertEquals("finish", p.current().id());
            assertTrue(p.onLastStep());
        });
    }

    @Test void workingAhead_skipsDoneSteps() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(TutorialScenario.world());
            TutorialProgress p = new TutorialProgress(TutorialScript.steps());
            p.next();
            e.setSpeed(Speed.X1);
            e.setSelection(Selection.site("site-earth-hub"));
            p.update(ctx(e));
            p.next();
            assertEquals("build-lab", p.current().id());
            // Colonizer before the lab.
            e.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
            e.tick();
            assertStep(p, e, "build-lab");
            e.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
            e.enqueue(new QueueResearchCommand("basic-mining"));
            e.tick();
            assertStep(p, e, "dispatch");
        });
    }

    @Test void boldLabels_areAllKnown() {
        Pattern bold = Pattern.compile("<b>(.*?)</b>");
        for (TutorialStep s : TutorialScript.steps()) {
            Matcher m = bold.matcher(s.html());
            while (m.find()) assertTrue(LABELS.contains(m.group(1)), s.id() + ": unknown label " + m.group(1));
        }
    }

    private static void assertStep(TutorialProgress p, Engine e, String id) {
        p.update(ctx(e));
        assertEquals(id, p.current().id());
    }
}
