package spacecolony.debug;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.sim.SimPhase;
import spacecolony.sim.World;

/**
 * Debug overlay (spec §8, design §4.2): status line, per-phase timings, the exceptions
 * banner and the sim controls. Mounted above the event strip while debug mode is on.
 */
public final class DebugOverlayPanel extends JPanel {
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    private static final Color ERROR = new Color(200, 40, 40);
    private static final int STATUS_MILLIS = 10_000;

    private final DebugController debug;
    private final Engine engine;
    private final JLabel statusLine = new JLabel();
    private final PhaseTableModel phases = new PhaseTableModel();
    private final JPanel banner = new JPanel(new BorderLayout(6, 0));
    private final JLabel bannerLabel = new JLabel();
    private final Timer refreshTimer = new Timer(500, e -> refresh());
    private final Timer statusClear = new Timer(STATUS_MILLIS, e -> { status = ""; refresh(); });
    private String status = "";

    DebugOverlayPanel(DebugController debug) {
        super(new BorderLayout(6, 2));
        this.debug = debug;
        this.engine = debug.engine();
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        setPreferredSize(new Dimension(0, 185)); // fits all 8 phase rows plus the banner
        statusClear.setRepeats(false);

        statusLine.setFont(MONO);
        add(statusLine, BorderLayout.NORTH);

        JTable table = new JTable(phases);
        table.setFont(MONO);
        table.setRowHeight(13);
        table.setFocusable(false);
        add(new JScrollPane(table), BorderLayout.CENTER);

        DebugActions a = debug.actions();
        JPanel buttons = new JPanel(new GridLayout(0, 2, 2, 2));
        for (Action act : List.of(a.step, a.runN, a.triggerEvent, a.dumpWorld, a.determinism, a.inspector, a.logViewer)) {
            JButton b = new JButton(act);
            b.setFocusable(false);
            b.setFont(MONO);
            buttons.add(b);
        }
        add(buttons, BorderLayout.EAST);

        bannerLabel.setForeground(ERROR);
        bannerLabel.setFont(MONO.deriveFont(Font.BOLD));
        JButton details = new JButton("Details…");
        details.setFocusable(false);
        details.addActionListener(e -> LogViewerDialog.show(debug.frame(), engine, Level.SEVERE));
        banner.add(bannerLabel, BorderLayout.CENTER);
        banner.add(details, BorderLayout.EAST);
        add(banner, BorderLayout.SOUTH);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.WorldReplaced
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

    /** Trailing status message (dump path, determinism result); cleared after 10 s. */
    public void setStatus(String message) {
        status = message == null ? "" : message;
        statusClear.restart();
        refresh();
    }

    void refresh() {
        World w = engine.world();
        statusLine.setText(String.format("tick %d · %.1f t/s · %s · queue %d · rng(seed=%d, tick=%d)%s",
            w.tick, engine.ticksPerSecond(), engine.speed(), engine.commandQueueDepth(), w.seed, w.tick,
            status.isEmpty() ? "" : " · " + status));
        phases.fireTableDataChanged();
        ExceptionLog log = debug.exceptions();
        long total = log.total();
        List<ExceptionLog.Entry> entries = log.snapshot();
        if (total == 0 || entries.isEmpty()) {
            banner.setVisible(false);
        } else {
            Throwable t = entries.get(entries.size() - 1).error();
            bannerLabel.setText(total + (total == 1 ? " exception" : " exceptions") + " · latest: "
                + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            banner.setVisible(true);
        }
    }

    // Visible for tests.
    String statusText() { return statusLine.getText(); }
    boolean bannerVisible() { return banner.isVisible(); }
    String bannerText() { return bannerLabel.getText(); }

    private final class PhaseTableModel extends AbstractTableModel {
        private final String[] cols = { "Phase", "Mean µs", "Max µs" };
        @Override public int getRowCount() { return SimPhase.values().length; }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int row, int col) {
            SimPhase p = SimPhase.values()[row];
            PhaseTimings.Stat s = debug.timings().stat(p);
            return switch (col) {
                case 0 -> p.name();
                case 1 -> String.format("%.1f", s.meanNanos() / 1000.0);
                default -> String.format("%.1f", s.maxNanos() / 1000.0);
            };
        }
    }
}
