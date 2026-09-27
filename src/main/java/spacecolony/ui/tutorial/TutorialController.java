package spacecolony.ui.tutorial;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.logging.Logger;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;
import spacecolony.sim.commands.SetRandomEventsCommand;
import spacecolony.tutorial.TutorialContext;
import spacecolony.tutorial.TutorialProgress;
import spacecolony.tutorial.TutorialScript;
import spacecolony.tutorial.TutorialStep;
import spacecolony.ui.GameSession;
import spacecolony.ui.SpaceColonyFrame;

/**
 * Runs the tutorial inside a real game frame (Plan 6 §5.6): watches the engine, advances the
 * script, and keeps the coach card and highlight on the current step.
 */
public final class TutorialController implements CoachPanel.Listener {
    private static final Logger LOG = Logger.getLogger("spacecolony.tutorial");

    private final SpaceColonyFrame frame;
    private final Engine engine;
    private final Runnable markCompleted;
    private final TutorialProgress progress = new TutorialProgress(TutorialScript.steps());
    private final CoachPanel card = new CoachPanel(this);
    private final HighlightLayer highlight = new HighlightLayer();
    private final EngineListener listener = this::onEvent;
    private final ComponentAdapter relayout = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) { layout(); }
    };
    private boolean active = true;
    private boolean completed;

    /** @param markCompleted records the tutorial as done in the options file (called once) */
    public TutorialController(SpaceColonyFrame frame, Engine engine, Runnable markCompleted) {
        this.frame = frame;
        this.engine = engine;
        this.markCompleted = markCompleted;
        JLayeredPane layers = frame.getLayeredPane();
        layers.add(highlight, JLayeredPane.PALETTE_LAYER);
        layers.add(card, JLayeredPane.PALETTE_LAYER, 0);
        layers.addComponentListener(relayout);
        engine.addListener(listener);
        render();
        SwingUtilities.invokeLater(this::layout);
    }

    TutorialProgress progress() { return progress; }
    CoachPanel card() { return card; }
    HighlightLayer highlight() { return highlight; }
    boolean active() { return active; }

    private void onEvent(EngineEvent e) {
        if (!active) return;
        if (e instanceof EngineEvent.WorldReplaced) {
            // New Game or Load from the File menu: the tutorial world is gone.
            end();
            frame.session().setMode(GameSession.Mode.NORMAL);
            return;
        }
        if (progress.update(context())) render();
        else retarget();
    }

    private TutorialContext context() {
        return new TutorialContext(engine.world(), engine.speed(), engine.selection(), engine.view());
    }

    private void render() {
        TutorialStep step = progress.current();
        LOG.info(() -> "Tutorial step " + (progress.index() + 1) + "/" + progress.size() + ": " + step.id());
        if (progress.onLastStep()) complete();
        card.show(step, progress.index(), progress.size(), true);
        layout();
        retarget();
    }

    /** Selection and world changes rebuild the dock's buttons, so the target is looked up again. */
    private void retarget() {
        // Detail panel rebuilds after its own listener runs; look once that has happened.
        SwingUtilities.invokeLater(() -> {
            if (!active) return;
            TutorialStep step = progress.current();
            Component target = TutorialTargets.findFirst(frame.getContentPane(), step.targets());
            highlight.setTarget(target);
            card.setHintVisible(target == null && !step.targets().isEmpty(), step.hint());
            layout();
        });
    }

    private void layout() {
        if (!active) return;
        JLayeredPane layers = frame.getLayeredPane();
        highlight.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        Component center = frame.centerView();
        int h = Math.min(card.preferredHeight(), Math.max(120, layers.getHeight() - 40));
        Rectangle c = center.isShowing()
            ? SwingUtilities.convertRectangle(center.getParent(), center.getBounds(), layers)
            : new Rectangle(0, 0, layers.getWidth(), layers.getHeight());
        card.setBounds(c.x + 16, Math.max(8, c.y + c.height - h - 16), CoachPanel.WIDTH, h);
        card.revalidate();
        layers.repaint();
    }

    // ---- card buttons ----

    @Override public void next() { progress.next(); progress.update(context()); render(); }

    @Override public void skip() { progress.skip(); progress.update(context()); render(); }

    @Override public void exit() {
        Object[] choices = { "Keep playing", "Main menu", "Cancel" };
        int r = JOptionPane.showOptionDialog(frame,
            "Leave the tutorial? You can keep playing this game or return to the main menu.",
            "Exit tutorial", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (r == 0) keepPlaying();
        else if (r == 1) mainMenu();
    }

    /** Turns the tutorial into a normal game: random events on, saving allowed. */
    @Override public void keepPlaying() {
        EdtGuard.assertEdt();
        engine.enqueue(new SetRandomEventsCommand(true));
        frame.session().setMode(GameSession.Mode.NORMAL);
        complete();
        end();
    }

    @Override public void mainMenu() {
        complete();
        frame.session().leave();
    }

    private void complete() {
        if (completed) return;
        completed = true;
        markCompleted.run();
    }

    /** Removes the card and highlight and stops listening. Safe to call twice. */
    private void end() {
        if (!active) return;
        active = false;
        engine.removeListener(listener);
        highlight.stop();
        JLayeredPane layers = frame.getLayeredPane();
        layers.removeComponentListener(relayout);
        layers.remove(card);
        layers.remove(highlight);
        layers.repaint();
    }

    /** For Main Menu and tests. */
    public void dispose() { end(); }
}
