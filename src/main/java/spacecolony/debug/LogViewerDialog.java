package spacecolony.debug;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;

/**
 * Debug log viewer (design §4.10). Log tab: the JUL ring, filtered by minimum level and
 * logger prefix, with follow and a stack-trace pane. Events tab: the world's full
 * recentEvents ring with severity and kind filters. Copy/Save act on the active tab.
 */
public final class LogViewerDialog extends JDialog {
    static final Level[] LEVELS = { Level.ALL, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE };
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final SimpleFormatter FORMATTER = new SimpleFormatter();

    private final Engine engine;
    private final RingBufferHandler ring;
    private final JTabbedPane tabs = new JTabbedPane();

    private final JComboBox<Level> minLevel = new JComboBox<>(LEVELS);
    private final JTextField loggerPrefix = new JTextField(14);
    private final JCheckBox follow = new JCheckBox("Follow", true);
    private final LogModel logModel = new LogModel();
    private final JTable logTable = new JTable(logModel);
    private final JTextArea stack = new JTextArea(6, 80);
    private final Timer poll = new Timer(500, e -> refreshLogIfChanged());
    private long shownVersion = -1;

    private final JComboBox<Object> severity = new JComboBox<>(withAll(EventSeverity.values()));
    private final JComboBox<Object> kind = new JComboBox<>(withAll(EventKind.values()));
    private final EventModel eventModel = new EventModel();
    private final EngineListener listener = e -> {
        if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.WorldReplaced) refreshEvents();
    };

    LogViewerDialog(Window owner, Engine engine, RingBufferHandler ring, Level initialMinLevel) {
        super(owner, "Log viewer", ModalityType.MODELESS);
        this.engine = engine;
        this.ring = ring;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // ----- Log tab -----
        minLevel.setSelectedItem(initialMinLevel);
        minLevel.addActionListener(e -> refreshLog());
        loggerPrefix.getDocument().addDocumentListener(onChange(this::refreshLog));
        logTable.setFont(MONO);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.getSelectionModel().addListSelectionListener(e -> showStack());
        logTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(600);
        stack.setEditable(false);
        stack.setFont(MONO);
        JPanel logBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        logBar.add(new JLabel("Level ≥"));
        logBar.add(minLevel);
        logBar.add(new JLabel("Logger:"));
        logBar.add(loggerPrefix);
        logBar.add(follow);
        JPanel logTab = new JPanel(new BorderLayout());
        logTab.add(logBar, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(logTable), new JScrollPane(stack));
        split.setResizeWeight(0.75);
        logTab.add(split, BorderLayout.CENTER);
        tabs.addTab("Log", logTab);

        // ----- Events tab -----
        severity.addActionListener(e -> refreshEvents());
        kind.addActionListener(e -> refreshEvents());
        JTable eventTable = new JTable(eventModel);
        eventTable.setFont(MONO);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(500);
        JPanel evBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        evBar.add(new JLabel("Severity:"));
        evBar.add(severity);
        evBar.add(new JLabel("Kind:"));
        evBar.add(kind);
        JPanel evTab = new JPanel(new BorderLayout());
        evTab.add(evBar, BorderLayout.NORTH);
        evTab.add(new JScrollPane(eventTable), BorderLayout.CENTER);
        tabs.addTab("Events", evTab);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(visibleText()), null));
        JButton save = new JButton("Save…");
        save.addActionListener(e -> save());
        bottom.add(copy);
        bottom.add(save);

        add(tabs, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(900, 520));
        pack();
        setLocationRelativeTo(owner);

        engine.addListener(listener);
        refreshLog();
        refreshEvents();
    }

    /** Opens a viewer over the app's installed ring (an empty ring when logging isn't installed). */
    public static LogViewerDialog show(Component parent, Engine engine, Level initialMinLevel) {
        DebugLogging.Installed inst = DebugLogging.current();
        RingBufferHandler ring = inst == null ? new RingBufferHandler(1) : inst.ring();
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        LogViewerDialog d = new LogViewerDialog(owner, engine, ring, initialMinLevel);
        d.setVisible(true);
        return d;
    }

    @Override public void addNotify() {
        super.addNotify();
        poll.start();
    }

    @Override public void dispose() {
        poll.stop();
        engine.removeListener(listener);
        super.dispose();
    }

    // ----- log tab -----

    private void refreshLogIfChanged() {
        if (ring.version() != shownVersion) refreshLog();
    }

    void refreshLog() {
        shownVersion = ring.version();
        List<LogRecord> rows = new ArrayList<>();
        Level min = (Level) minLevel.getSelectedItem();
        for (LogRecord r : ring.snapshot()) if (matches(r, min, loggerPrefix.getText())) rows.add(r);
        logModel.rows = rows;
        logModel.fireTableDataChanged();
        if (follow.isSelected() && !rows.isEmpty()) {
            logTable.scrollRectToVisible(logTable.getCellRect(rows.size() - 1, 0, true));
        }
    }

    static boolean matches(LogRecord r, Level min, String prefix) {
        if (r.getLevel().intValue() < min.intValue()) return false;
        String p = prefix == null ? "" : prefix.trim();
        if (p.isEmpty()) return true;
        String name = r.getLoggerName() == null ? "" : r.getLoggerName();
        return name.startsWith(p) || name.startsWith(DebugLogging.ROOT + "." + p);
    }

    private void showStack() {
        int row = logTable.getSelectedRow();
        if (row < 0 || row >= logModel.rows.size()) { stack.setText(""); return; }
        stack.setText(stackText(logModel.rows.get(row)));
        stack.setCaretPosition(0);
    }

    static String stackText(LogRecord r) {
        if (r.getThrown() == null) return "";
        StringWriter sw = new StringWriter();
        r.getThrown().printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static String shortLogger(String name) {
        if (name == null) return "";
        return name.startsWith(DebugLogging.ROOT + ".") ? name.substring(DebugLogging.ROOT.length() + 1) : name;
    }

    // ----- events tab -----

    void refreshEvents() {
        List<Event> rows = new ArrayList<>();
        Object sev = severity.getSelectedItem();
        Object k = kind.getSelectedItem();
        for (Event e : engine.world().recentEvents) {
            if (sev instanceof EventSeverity s && e.severity() != s) continue;
            if (k instanceof EventKind ek && e.kind() != ek) continue;
            rows.add(e);
        }
        eventModel.rows = rows;
        eventModel.fireTableDataChanged();
    }

    // ----- copy / save -----

    /** Visible rows of the active tab as tab-separated text. */
    String visibleText() {
        AbstractTableModel m = tabs.getSelectedIndex() == 0 ? logModel : eventModel;
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < m.getRowCount(); r++) {
            for (int c = 0; c < m.getColumnCount(); c++) {
                if (c > 0) sb.append('\t');
                sb.append(m.getValueAt(r, c));
            }
            sb.append('\n');
            if (m == logModel) {
                String st = stackText(logModel.rows.get(r));
                if (!st.isEmpty()) sb.append(st);
            }
        }
        return sb.toString();
    }

    private void save() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Save log");
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = ch.getSelectedFile().toPath();
        String text = visibleText();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Files.writeString(file, text, StandardCharsets.UTF_8);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(LogViewerDialog.this, "Could not save: " + cause.getMessage(),
                        "Save log", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static Object[] withAll(Object[] values) {
        Object[] out = new Object[values.length + 1];
        out[0] = "ALL";
        System.arraycopy(values, 0, out, 1, values.length);
        return out;
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { r.run(); }
            @Override public void removeUpdate(DocumentEvent e) { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // Visible for tests.
    JComboBox<Level> minLevelBox() { return minLevel; }
    JTextField loggerPrefixField() { return loggerPrefix; }
    JTable logTable() { return logTable; }
    String stackPaneText() { return stack.getText(); }
    int eventRowCount() { return eventModel.getRowCount(); }
    JComboBox<Object> kindBox() { return kind; }
    JTabbedPane tabs() { return tabs; }

    private static final class LogModel extends AbstractTableModel {
        List<LogRecord> rows = List.of();
        private final String[] cols = { "Time", "Level", "Logger", "Message" };
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int row, int col) {
            LogRecord r = rows.get(row);
            return switch (col) {
                case 0 -> TIME.format(Instant.ofEpochMilli(r.getMillis()));
                case 1 -> r.getLevel().getName();
                case 2 -> shortLogger(r.getLoggerName());
                default -> FORMATTER.formatMessage(r);
            };
        }
    }

    private static final class EventModel extends AbstractTableModel {
        List<Event> rows = List.of();
        private final String[] cols = { "Date", "Severity", "Kind", "Message" };
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int row, int col) {
            Event e = rows.get(row);
            return switch (col) {
                case 0 -> String.format("Y%d D%d", e.tick() / 365, e.tick() % 365 + 1);
                case 1 -> e.severity();
                case 2 -> e.kind();
                default -> e.message();
            };
        }
    }
}
