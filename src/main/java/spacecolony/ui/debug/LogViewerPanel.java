package spacecolony.ui.debug;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import spacecolony.debug.DebugLogging;
import spacecolony.debug.RingBufferHandler;

/**
 * Log viewer content (spec §8): the in-memory log stream (engine/sim JUL records plus the
 * mirrored world event log), filterable by minimum level and logger-name prefix, with
 * tail-follow, copy and save. Also sets the runtime log level.
 */
public class LogViewerPanel extends JPanel {
    static final Level[] LEVELS = { Level.ALL, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE };
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final RingBufferHandler buffer;
    private final JTextArea text = new JTextArea();
    private final JComboBox<Level> minLevel = new JComboBox<>(LEVELS);
    private final JTextField source = new JTextField(14);
    private final JCheckBox follow = new JCheckBox("Tail", true);
    private final JComboBox<Level> captureLevel = new JComboBox<>(LEVELS);
    private final Timer poll = new Timer(500, e -> refreshIfChanged());
    private long shownSequence = -1;

    public LogViewerPanel(RingBufferHandler buffer) {
        super(new BorderLayout(4, 4));
        this.buffer = buffer;
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Show ≥"));
        top.add(minLevel);
        top.add(new JLabel("Source:"));
        top.add(source);
        top.add(follow);
        top.add(button("Copy", this::copy));
        top.add(button("Save…", this::save));
        top.add(button("Clear", () -> { buffer.clear(); refresh(); }));
        top.add(new JLabel("  Capture ≥"));
        captureLevel.setSelectedItem(closest(DebugLogging.level()));
        captureLevel.addActionListener(e -> DebugLogging.setLevel((Level) captureLevel.getSelectedItem()));
        top.add(captureLevel);

        minLevel.addActionListener(e -> refresh());
        source.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(text), BorderLayout.CENTER);
        refresh();
    }

    @Override public void addNotify() {
        super.addNotify();
        poll.start();
    }

    @Override public void removeNotify() {
        poll.stop();
        super.removeNotify();
    }

    private void refreshIfChanged() {
        if (buffer.sequence() != shownSequence) refresh();
    }

    void refresh() {
        shownSequence = buffer.sequence();
        text.setText(render(buffer, (Level) minLevel.getSelectedItem(), source.getText()));
        if (follow.isSelected()) text.setCaretPosition(text.getDocument().getLength());
    }

    /** Filtered, formatted log text; one record per line plus any stack trace. */
    static String render(RingBufferHandler buffer, Level min, String sourcePrefix) {
        StringBuilder sb = new StringBuilder();
        for (LogRecord r : buffer.snapshot()) if (matches(r, min, sourcePrefix)) sb.append(format(r));
        return sb.toString();
    }

    static boolean matches(LogRecord r, Level min, String sourcePrefix) {
        if (r.getLevel().intValue() < min.intValue()) return false;
        String p = sourcePrefix == null ? "" : sourcePrefix.trim();
        if (p.isEmpty()) return true;
        String name = r.getLoggerName() == null ? "" : r.getLoggerName();
        return name.startsWith(p) || name.startsWith(DebugLogging.ROOT + "." + p);
    }

    static String format(LogRecord r) {
        StringBuilder sb = new StringBuilder()
            .append(TIME.format(Instant.ofEpochMilli(r.getMillis()))).append(' ')
            .append(String.format("%-7s", r.getLevel().getName())).append(' ')
            .append(r.getLoggerName()).append(" - ")
            .append(r.getMessage()).append('\n');
        if (r.getThrown() != null) {
            StringWriter sw = new StringWriter();
            r.getThrown().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }

    private void copy() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.getText()), null);
    }

    private void save() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Save log");
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.writeString(ch.getSelectedFile().toPath(), text.getText(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save log: " + ex.getMessage(),
                "Save log", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Level closest(Level l) {
        Level best = Level.ALL;
        for (Level c : LEVELS) if (c.intValue() <= l.intValue()) best = c;
        return best;
    }

    private static JButton button(String label, Runnable r) {
        JButton b = new JButton(label);
        b.addActionListener(e -> r.run());
        return b;
    }

    // Visible for tests.
    String shownText() { return text.getText(); }
    JComboBox<Level> minLevelBox() { return minLevel; }
    JTextField sourceField() { return source; }
}
