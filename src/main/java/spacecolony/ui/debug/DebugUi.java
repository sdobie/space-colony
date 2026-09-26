package spacecolony.ui.debug;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import spacecolony.debug.DebugController;
import spacecolony.debug.DebugLogging;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;

/**
 * Wires debug mode into the main window: the {@link DebugController}, the bottom overlay,
 * a "Debug" menu (shown only while debug mode is on) and the Ctrl+D toggle.
 */
public final class DebugUi {
    static final KeyStroke TOGGLE_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK);

    private final Engine engine;
    private final DebugController controller;
    private final DebugOverlayPanel overlay;
    private final JMenu menu = new JMenu("Debug");
    private final JCheckBoxMenuItem showOverlay = new JCheckBoxMenuItem("Show overlay", true);

    public DebugUi(Engine engine) {
        this.engine = engine;
        this.controller = new DebugController(engine);
        this.overlay = new DebugOverlayPanel(controller, DebugLogging.buffer());

        showOverlay.addActionListener(e -> syncVisibility());
        menu.add(showOverlay);
        menu.add(item("Inspect selection…", () -> {
            if (DebugController.resolve(engine, engine.selection()) != null) {
                ObjectInspectorDialog.show(overlay, engine, engine.selection());
            } else {
                JOptionPane.showMessageDialog(overlay, "Select a body, site or ship first.",
                    "Inspect", JOptionPane.INFORMATION_MESSAGE);
            }
        }));
        menu.add(item("Log viewer…", () -> LogViewerDialog.show(overlay, DebugLogging.buffer())));
        menu.add(item("Trigger event…", () -> TriggerEventDialog.show(overlay, controller)));
        menu.addSeparator();
        // No accelerator here: the root-pane binding already owns Ctrl+D, and a second
        // binding would toggle twice.
        menu.add(item("Leave debug mode (Ctrl+D)", () -> engine.setDebugEnabled(false)));

        engine.addListener(e -> { if (e instanceof EngineEvent.DebugModeChanged) syncVisibility(); });
        syncVisibility();
    }

    /**
     * Bind Ctrl+D on the window so it works whichever panel has focus, including while the
     * Debug menu is hidden.
     */
    public void installKeyBinding(JRootPane root) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(TOGGLE_KEY, "toggleDebug");
        root.getActionMap().put("toggleDebug", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { controller.toggleDebug(); }
        });
    }

    public DebugController controller() { return controller; }
    public JComponent overlay() { return overlay; }
    public JMenu menu() { return menu; }

    private void syncVisibility() {
        boolean on = engine.debugEnabled();
        menu.setVisible(on);
        overlay.setVisible(on && showOverlay.isSelected());
        overlay.revalidate();
    }

    private JMenuItem item(String label, Runnable r) {
        JMenuItem i = new JMenuItem(label);
        i.addActionListener(e -> {
            try {
                r.run();
            } catch (IllegalStateException | IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(overlay, ex.getMessage(), label, JOptionPane.WARNING_MESSAGE);
            }
        });
        return i;
    }
}
