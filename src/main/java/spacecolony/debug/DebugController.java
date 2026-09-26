package spacecolony.debug;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;

/**
 * Owns debug-mode UI lifecycle. Constructed once by SpaceColonyFrame; listens for
 * DebugModeChanged and mounts/unmounts the overlay, Debug menu, phase timings, and map
 * overlays. Ctrl+D is bound on the frame's root pane.
 */
public final class DebugController {
    public static final KeyStroke TOGGLE_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK);
    public static final KeyStroke STEP_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);
    public static final int TIMING_WINDOW = 50;

    private final JFrame frame;
    private final JMenuBar menuBar;
    private final JPanel southStack;
    private final Engine engine;
    private final ExceptionLog exceptions;
    private final Runnable repaintMap;
    private final DebugActions actions;
    private final DebugOverlayPanel overlay;
    private final DebugMenu menu;
    private PhaseTimings timings = new PhaseTimings(TIMING_WINDOW);
    private boolean mapOverlays = true;
    private boolean mounted;

    public DebugController(JFrame frame, JMenuBar menuBar, JPanel southStack, Engine engine,
                           ExceptionLog exceptions, Runnable repaintMap) {
        EdtGuard.assertEdt();
        this.frame = frame;
        this.menuBar = menuBar;
        this.southStack = southStack;
        this.engine = engine;
        this.exceptions = exceptions;
        this.repaintMap = repaintMap;
        this.actions = new DebugActions(this);
        this.overlay = new DebugOverlayPanel(this);
        this.menu = new DebugMenu(this);

        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(TOGGLE_KEY, "toggleDebug");
        frame.getRootPane().getActionMap().put("toggleDebug", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { engine.setDebugEnabled(!engine.debugEnabled()); }
        });
        engine.addListener(e -> { if (e instanceof EngineEvent.DebugModeChanged) apply(); });
        apply();
    }

    public JFrame frame() { return frame; }
    public Engine engine() { return engine; }
    public ExceptionLog exceptions() { return exceptions; }
    public PhaseTimings timings() { return timings; }
    public DebugActions actions() { return actions; }
    public DebugOverlayPanel overlay() { return overlay; }
    public DebugMenu menu() { return menu; }

    /** True when debug is on and the "Map overlays" toggle is on (default on). */
    public boolean mapOverlaysOn() { return engine.debugEnabled() && mapOverlays; }

    void setMapOverlays(boolean on) {
        mapOverlays = on;
        repaintMap.run();
    }
    boolean mapOverlaysToggle() { return mapOverlays; }

    private void apply() {
        boolean on = engine.debugEnabled();
        if (on == mounted) return;
        mounted = on;
        if (on) {
            timings = new PhaseTimings(TIMING_WINDOW);
            engine.setPhaseObserver(timings);
            southStack.add(overlay, 0);
            menuBar.add(menu);
            // The look and feel binds F10 to "focus the menu bar", which would swallow the
            // Step accelerator; mask it only while debug mode owns F10.
            menuBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(STEP_KEY, "none");
        } else {
            engine.setPhaseObserver(null);
            southStack.remove(overlay);
            menuBar.remove(menu);
            menuBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(STEP_KEY);
        }
        southStack.revalidate();
        southStack.repaint();
        menuBar.revalidate();
        menuBar.repaint();
        repaintMap.run();
    }
}
