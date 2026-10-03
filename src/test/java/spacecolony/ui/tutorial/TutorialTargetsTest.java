package spacecolony.ui.tutorial;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.testutil.Edt;
import spacecolony.tutorial.TutorialScenario;
import spacecolony.tutorial.TutorialScript;
import spacecolony.ui.BodyViewPanel;
import spacecolony.ui.ColonyListPanel;
import spacecolony.ui.DetailPanel;
import spacecolony.ui.GameSession;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.ui.TopBar;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The tutorial on a real game window: every step's highlight finds its button (Plan 6 §5.4). */
class TutorialTargetsTest {
    private Engine engine;
    private SpaceColonyFrame frame;
    private TutorialController tutorial;
    private final AtomicInteger completed = new AtomicInteger();

    @BeforeEach void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; run under xvfb-run");
        Edt.run(() -> {
            engine = new Engine(TutorialScenario.world());
            frame = new SpaceColonyFrame(engine);
            frame.gameLoop().dispose();
            frame.session().setMode(GameSession.Mode.TUTORIAL);
            frame.setSize(1280, 800);
            frame.setVisible(true);
            tutorial = new TutorialController(frame, engine, completed::incrementAndGet);
        });
        flush();
    }

    @AfterEach void close() throws Exception {
        if (frame != null) Edt.run(() -> { tutorial.dispose(); frame.dispose(); });
    }

    @Test void targetNames_matchTheScriptCopies() {
        assertEquals(TopBar.TARGET_X1, TutorialScript.T_X1);
        assertEquals(TopBar.TARGET_X16, TutorialScript.T_X16);
        assertEquals(TopBar.TARGET_TECH, TutorialScript.T_TECH);
        assertEquals(TopBar.TARGET_GOALS, TutorialScript.T_GOALS);
        assertEquals(ColonyListPanel.TARGET, TutorialScript.T_COLONY_LIST);
        assertEquals(ColonyListPanel.cardName(TutorialScript.HUB), TutorialScript.T_HUB_CARD);
        assertEquals(DetailPanel.TARGET, TutorialScript.T_DETAIL);
        assertEquals(DetailPanel.TARGET_BUILD_BUILDING, TutorialScript.T_BUILD_BUILDING);
        assertEquals(DetailPanel.TARGET_BUILD_SHIP, TutorialScript.T_BUILD_SHIP);
        assertEquals(DetailPanel.TARGET_DISPATCH, TutorialScript.T_DISPATCH);
        assertEquals(DetailPanel.TARGET_FOUND_COLONY, TutorialScript.T_FOUND_COLONY);
        assertEquals(BodyViewPanel.TARGET_SPHERE, TutorialScript.T_SPHERE);
    }

    @Test void everyStep_highlightsItsTarget_withTheLabelTheCardNames() throws Exception {
        expect("welcome", null, null);
        Edt.run(() -> tutorial.next());
        flush();
        expect("start-clock", TopBar.TARGET_X1, "1×");

        act(() -> engine.setSpeed(Speed.X1));
        expect("select-hub", TutorialScript.T_HUB_CARD, null);
        act(() -> pressHighlightedTarget());
        expect("read-dock", DetailPanel.TARGET, null);
        Edt.run(() -> tutorial.next());
        flush();
        expect("build-lab", DetailPanel.TARGET_BUILD_BUILDING, "Build building...");

        act(() -> { engine.enqueue(new BuildBuildingCommand(TutorialScript.HUB, BuildingType.RESEARCH_LAB)); engine.tick(); });
        expect("research", TopBar.TARGET_TECH, "Tech");
        act(() -> { engine.enqueue(new QueueResearchCommand("basic-mining")); engine.tick(); });
        expect("build-colonizer", DetailPanel.TARGET_BUILD_SHIP, "Build ship...");
        act(() -> { engine.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, TutorialScript.HUB)); engine.tick(); });

        // Dispatch step with the hub still selected: no Dispatch button, so the card shows its hint.
        assertEquals("dispatch", tutorial.progress().current().id());
        assertNull(tutorial.highlight().target());
        act(() -> engine.setSelection(Selection.ship("c1")));
        expect("dispatch", DetailPanel.TARGET_DISPATCH, "Dispatch...");

        act(() -> {
            engine.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of(Resource.FOOD, 40.0)));
            engine.tick();
        });
        expect("fast-forward", TopBar.TARGET_X16, "16×");
        act(() -> {
            engine.setSpeed(Speed.X16);
            for (int i = 0; i < 30 && engine.world().findShip("c1").orbitingBodyId == null; i++) engine.tick();
        });
        expect("found-colony", DetailPanel.TARGET_FOUND_COLONY, "Found colony…");

        // The click still reaches the button under the highlight.
        Edt.run(() -> {
            Component target = tutorial.highlight().target();
            JLayeredPane layers = frame.getLayeredPane();
            Point p = SwingUtilities.convertPoint(target, target.getWidth() / 2, target.getHeight() / 2, layers);
            assertFalse(tutorial.highlight().contains(p.x, p.y));
            assertSame(target, SwingUtilities.getDeepestComponentAt(layers, p.x, p.y));
            ((AbstractButton) target).doClick();
            assertEquals(EngineEvent.ViewChanged.View.BODY_VIEW, engine.view());
        });
        flush();
        expect("found-colony", BodyViewPanel.TARGET_SPHERE, null);
        assertEquals(0, completed.get());
    }

    @Test void nextStep_isOnEveryStep_andAdvancesReadingAndActionSteps() throws Exception {
        for (int i = 0; i < 11; i++) {
            int index = i;
            Edt.run(() -> {
                assertEquals(index, tutorial.progress().index());
                assertEquals(List.of("Exit tutorial", "Next step"), tutorial.card().buttonLabels());
                AbstractButton next = findButton(tutorial.card(), "Next step");
                assertNotNull(next);
                next.doClick();
            });
            flush();
        }
        Edt.run(() -> assertTrue(tutorial.progress().onLastStep()));
    }

    private static AbstractButton findButton(java.awt.Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton b && text.equals(b.getText())) return b;
            if (k instanceof java.awt.Container kc) {
                AbstractButton hit = findButton(kc, text);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @Test void lastStep_marksComplete_andKeepPlayingEndsTutorialMode() throws Exception {
        Edt.run(() -> { for (int i = 0; i < 11; i++) tutorial.skip(); });
        flush();
        Edt.run(() -> {
            assertTrue(tutorial.progress().onLastStep());
            assertEquals(1, completed.get());
            assertEquals(List.of("Main menu", "Keep playing"), tutorial.card().buttonLabels());
            tutorial.keepPlaying();
            engine.tick();
            assertTrue(engine.world().randomEventsEnabled);
            assertEquals(GameSession.Mode.NORMAL, frame.session().mode());
            assertFalse(tutorial.active());
            assertNull(tutorial.card().getParent());
        });
        assertEquals(1, completed.get());
    }

    @Test void newGameFromFileMenu_endsTheTutorial() throws Exception {
        Edt.run(() -> {
            engine.reset(spacecolony.world.WorldGenerator.generate(3L));
            assertFalse(tutorial.active());
            assertEquals(GameSession.Mode.NORMAL, frame.session().mode());
        });
    }

    /** Presses the mouse on whatever sits under the centre of the highlighted target, as a player would. */
    private void pressHighlightedTarget() {
        Component target = tutorial.highlight().target();
        JLayeredPane layers = frame.getLayeredPane();
        Point p = SwingUtilities.convertPoint(target, target.getWidth() / 2, target.getHeight() / 2, layers);
        Component hit = SwingUtilities.getDeepestComponentAt(layers, p.x, p.y);
        assertSame(target, hit);
        Point q = SwingUtilities.convertPoint(layers, p, hit);
        hit.dispatchEvent(new MouseEvent(hit, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            InputEvent.BUTTON1_DOWN_MASK, q.x, q.y, 1, false, MouseEvent.BUTTON1));
    }

    private void act(Edt.Body body) throws Exception {
        Edt.run(body);
        flush();
    }

    /** Lets the retarget and panel rebuilds queued with invokeLater run. */
    private static void flush() throws Exception {
        for (int i = 0; i < 3; i++) Edt.run(() -> { });
    }

    private void expect(String step, String targetName, String label) throws Exception {
        Edt.run(() -> {
            assertEquals(step, tutorial.progress().current().id());
            Component t = tutorial.highlight().target();
            if (targetName == null) { assertNull(t); return; }
            assertNotNull(t, step + ": no target " + targetName);
            assertEquals(targetName, t.getName());
            assertTrue(t.isShowing());
            if (label != null) assertEquals(label, ((AbstractButton) t).getText());
            assertTrue(tutorial.card().isShowing());
        });
    }
}
