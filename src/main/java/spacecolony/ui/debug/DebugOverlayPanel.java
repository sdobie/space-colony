package spacecolony.ui.debug;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.LogRecord;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import spacecolony.debug.DebugController;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.RingBufferHandler;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.sim.Simulator;
import spacecolony.ui.UiColors;

/**
 * Debug overlay (spec §8): bottom panel with live sim stats, phase timings, recent
 * exceptions, and the sim controls (step, run N, trigger event, inspect, logs).
 */
public class DebugOverlayPanel extends JPanel {
    private final DebugController debug;
    private final Engine engine;
    private final RingBufferHandler logBuffer;
    private final JLabel statsLabel = label();
    private final JLabel phasesLabel = label();
    private final JLabel exceptionsLabel = label();
    private final JButton stepBtn = new JButton("Step 1 tick");
    private final JSpinner runCount = new JSpinner(new SpinnerNumberModel(100, 1, DebugController.MAX_RUN_TICKS, 100));
    /** Repaints the tick-rate readout while ticks are flowing; runs only while shown. */
    private final Timer refreshTimer = new Timer(500, e -> refresh());

    public DebugOverlayPanel(DebugController debug, RingBufferHandler logBuffer) {
        this.debug = debug;
        this.engine = debug.engine();
        this.logBuffer = logBuffer;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.WARNING),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel stats = new JPanel(new GridLayout(0, 1));
        stats.setOpaque(false);
        stats.add(statsLabel);
        stats.add(phasesLabel);
        stats.add(exceptionsLabel);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        controls.setOpaque(false);
        stepBtn.setFocusable(false);
        stepBtn.addActionListener(e -> debug.stepOne());
        controls.add(stepBtn);
        controls.add(runCount);
        controls.add(button("Run N ticks", () -> debug.runTicks((Integer) runCount.getValue())));
        controls.add(button("Trigger event…", () -> TriggerEventDialog.show(this, debug)));
        controls.add(button("Inspect selection…", this::inspectSelection));
        controls.add(button("Log viewer…", () -> LogViewerDialog.show(this, logBuffer)));

        // Controls above, stats below at full width so the long timing line isn't clipped.
        add(controls, BorderLayout.NORTH);
        add(stats, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SpeedChanged) refresh();
        });
        refresh();
    }

    @Override public void addNotify() {
        super.addNotify();
        refreshTimer.start();
    }

    @Override public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    void refresh() {
        var w = engine.world();
        statsLabel.setText(String.format(
            "Tick %d  ·  %.1f ticks/s  ·  queue %d  ·  RNG seed %d (tick-keyed)  ·  last tick %.2f ms",
            w.tick, debug.ticksPerSecond(), engine.pendingCommandCount(), w.seed,
            debug.timings().lastTotalNanos() / 1e6));

        long[] avg = debug.timings().averageNanos();
        long[] max = debug.timings().maxNanos();
        StringBuilder sb = new StringBuilder("Phases avg/max ms (last " + debug.timings().sampleCount() + "):");
        for (int i = 0; i < avg.length; i++) {
            sb.append(String.format("  %s %.2f/%.2f", Simulator.PHASE_NAMES[i], avg[i] / 1e6, max[i] / 1e6));
        }
        phasesLabel.setText(sb.toString());

        List<LogRecord> exs = DebugLogging.recentExceptions(logBuffer, 5);
        if (exs.isEmpty()) {
            exceptionsLabel.setText("Exceptions: none");
            exceptionsLabel.setForeground(UiColors.FOREGROUND_DIM);
        } else {
            LogRecord last = exs.get(exs.size() - 1);
            Throwable t = last.getThrown();
            String when = new SimpleDateFormat("HH:mm:ss").format(new Date(last.getMillis()));
            exceptionsLabel.setText("Exceptions (" + exs.size() + " recent): last at " + when + "  "
                + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            exceptionsLabel.setForeground(UiColors.ERROR);
        }
        stepBtn.setEnabled(engine.speed().isPaused());
    }

    private void inspectSelection() {
        if (DebugController.resolve(engine, engine.selection()) == null) {
            JOptionPane.showMessageDialog(this, "Select a body, site or ship first (or Shift+click one).",
                "Inspect", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ObjectInspectorDialog.show(this, engine, engine.selection());
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(e -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), ex.getMessage(),
                    text, JOptionPane.WARNING_MESSAGE);
            }
        });
        return b;
    }

    private static JLabel label() {
        JLabel l = new JLabel();
        l.setForeground(UiColors.FOREGROUND);
        l.setFont(l.getFont().deriveFont(11f));
        return l;
    }

    // Visible for tests.
    String statsText() { return statsLabel.getText(); }
    String exceptionsText() { return exceptionsLabel.getText(); }
    boolean stepEnabled() { return stepBtn.isEnabled(); }
    Component stepButton() { return stepBtn; }
}
