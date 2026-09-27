package spacecolony.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import spacecolony.debug.DebugController;
import spacecolony.debug.DebugMenu;
import spacecolony.engine.Speed;
import spacecolony.options.Options;
import spacecolony.ui.FileMenu;
import spacecolony.ui.UiColors;

/**
 * Options (Plan 6 §4.3): Gameplay, Display, Controls (read-only shortcut list) and Developer
 * tabs. Returns the edited options, or null when cancelled; the caller saves and applies them.
 */
public final class OptionsDialog extends JPanel {
    static final boolean MAC = System.getProperty("os.name", "").startsWith("Mac");

    private final boolean tutorialCompleted;
    private final JComboBox<Speed> startSpeed = combo(Options.START_SPEEDS,
        s -> s == Speed.PAUSED ? "Paused" : "1×");
    private final JComboBox<Integer> autosave = combo(Options.AUTOSAVE_MINUTES,
        m -> m == 0 ? "Off" : "Every " + m + " minutes");
    private final JCheckBox confirmQuit = new JCheckBox("Confirm before quitting");
    private final JCheckBox showSplash = new JCheckBox("Show splash screen");
    private final JCheckBox startMaximized = new JCheckBox("Start maximized");
    private final JComboBox<Integer> uiScale = combo(Options.UI_SCALES,
        p -> p == 0 ? "Automatic" : p + "%");
    private final JCheckBox startInDebug = new JCheckBox("Start in debug mode");
    private final JComboBox<Level> logLevel = combo(Options.LOG_LEVELS, Level::getName);
    final JTable controls = new JTable();
    final JTabbedPane tabs = new JTabbedPane();

    OptionsDialog(Options current) {
        super(new BorderLayout());
        this.tutorialCompleted = current.tutorialCompleted();

        JPanel gameplay = form();
        row(gameplay, 0, "Starting speed:", startSpeed);
        row(gameplay, 1, "Autosave:", autosave);
        row(gameplay, 2, null, confirmQuit);
        tabs.addTab("Gameplay", gameplay);

        JPanel display = form();
        row(display, 0, null, showSplash);
        row(display, 1, null, startMaximized);
        row(display, 2, "Interface scale:", uiScale);
        JLabel note = new JLabel(MAC ? "macOS applies display scaling itself."
                                     : "Takes effect the next time the game starts.");
        note.setForeground(UiColors.FOREGROUND_DIM);
        row(display, 3, null, note);
        if (MAC) uiScale.setEnabled(false);
        tabs.addTab("Display", display);

        DefaultTableModel model = new DefaultTableModel(new Object[] {"Action", "Shortcut"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (String[] row : shortcutRows()) model.addRow(row);
        controls.setModel(model);
        controls.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(controls);
        scroll.setPreferredSize(new java.awt.Dimension(420, 220));
        tabs.addTab("Controls", scroll);

        JPanel dev = form();
        row(dev, 0, null, startInDebug);
        row(dev, 1, "Log level:", logLevel);
        tabs.addTab("Developer", dev);

        add(tabs, BorderLayout.CENTER);
        load(current);
    }

    /** Shows the dialog modally over {@code owner}; returns the edited options or null. */
    public static Options show(Component owner, Options current) {
        Window w = owner instanceof Window ow ? ow : owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, "Options", JDialog.ModalityType.APPLICATION_MODAL);
        OptionsDialog content = new OptionsDialog(current);
        Options[] result = { null };
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        JButton defaults = new JButton("Restore defaults");
        ok.addActionListener(e -> { result[0] = content.collect(); d.dispose(); });
        cancel.addActionListener(e -> d.dispose());
        defaults.addActionListener(e -> content.load(content.collect().restoredDefaults()));
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(defaults);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        right.add(ok);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        d.getContentPane().add(content, BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }

    void load(Options o) {
        startSpeed.setSelectedItem(o.startSpeed());
        autosave.setSelectedItem(o.autosaveMinutes());
        confirmQuit.setSelected(o.confirmQuit());
        showSplash.setSelected(o.showSplash());
        startMaximized.setSelected(o.startMaximized());
        uiScale.setSelectedItem(o.uiScalePercent());
        startInDebug.setSelected(o.startInDebug());
        logLevel.setSelectedItem(o.logLevel());
    }

    Options collect() {
        return new Options((Speed) startSpeed.getSelectedItem(), (Integer) autosave.getSelectedItem(),
            confirmQuit.isSelected(), showSplash.isSelected(), startMaximized.isSelected(),
            (Integer) uiScale.getSelectedItem(), startInDebug.isSelected(),
            (Level) logLevel.getSelectedItem(), tutorialCompleted);
    }

    JComboBox<Integer> autosaveCombo() { return autosave; }

    /** Every keyboard shortcut, from the same KeyStroke constants the menus bind. */
    static List<String[]> shortcutRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Save", text(FileMenu.SAVE_KEY)});
        rows.add(new String[] {"Save As…", text(FileMenu.SAVE_AS_KEY)});
        rows.add(new String[] {"Load…", text(FileMenu.LOAD_KEY)});
        rows.add(new String[] {"Quit", text(FileMenu.QUIT_KEY)});
        rows.add(new String[] {"Game speed", "⏸ 1× 4× 16× buttons in the top bar"});
        rows.add(new String[] {"Toggle debug mode", text(DebugController.TOGGLE_KEY)});
        rows.add(new String[] {"Debug: step one tick (paused)", text(DebugController.STEP_KEY)});
        rows.add(new String[] {"Debug: run N ticks", text(DebugMenu.RUN_N_KEY)});
        rows.add(new String[] {"Debug: object inspector", text(DebugMenu.INSPECTOR_KEY)});
        rows.add(new String[] {"Debug: log viewer", text(DebugMenu.LOG_VIEWER_KEY)});
        rows.add(new String[] {"Debug: inspect an object", "Shift+click (debug mode)"});
        return rows;
    }

    static String text(KeyStroke k) {
        String mods = KeyEvent.getModifiersExText(k.getModifiers());
        String key = KeyEvent.getKeyText(k.getKeyCode());
        return mods.isEmpty() ? key : mods + "+" + key;
    }

    private static <T> JComboBox<T> combo(List<T> values, Function<T, String> label) {
        @SuppressWarnings("unchecked")
        JComboBox<T> c = new JComboBox<>((T[]) values.toArray());
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focus) {
                @SuppressWarnings("unchecked") T v = (T) value;
                return super.getListCellRendererComponent(list, v == null ? "" : label.apply(v), index, selected, focus);
            }
        });
        return c;
    }

    private static JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return p;
    }

    private static void row(JPanel p, int y, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        if (label != null) { c.gridx = 0; p.add(new JLabel(label), c); }
        c.gridx = 1;
        c.weightx = 1;
        p.add(field, c);
    }
}
