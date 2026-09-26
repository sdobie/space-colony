package spacecolony.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import spacecolony.save.SaveSlots;
import spacecolony.save.SaveSlots.Slot;

/**
 * Slot picker for File → Save… and File → Load…. Lists saves with their timestamps (newest
 * first) and lets the player save to a named slot, load any slot or autosave, or delete one.
 */
public final class SaveSlotDialog {
    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SaveSlotDialog() {}

    /**
     * Asks for a slot to save into, confirming before overwriting an existing one.
     *
     * @return the validated slot name, or null if the player cancelled
     */
    public static String showSave(Component parent, SaveSlots slots, String currentSlot) {
        Picker picker = new Picker(slots, true, currentSlot);
        Object[] options = {"Save", "Cancel"};
        while (true) {
            int r = JOptionPane.showOptionDialog(parent, picker, "Save Game", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (r != 0) return null;
            String name;
            try {
                name = SaveSlots.validateName(picker.nameField.getText());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(parent, ex.getMessage(), "Save Game", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (slots.exists(name)) {
                int ok = JOptionPane.showConfirmDialog(parent, "Overwrite the save \"" + name + "\"?",
                    "Save Game", JOptionPane.OK_CANCEL_OPTION);
                if (ok != JOptionPane.OK_OPTION) continue;
            }
            return name;
        }
    }

    /**
     * Asks for a save (named slot or autosave) to load.
     *
     * @return the chosen slot, or null if the player cancelled
     */
    public static Slot showLoad(Component parent, SaveSlots slots) {
        Picker picker = new Picker(slots, false, null);
        Object[] options = {"Load", "Cancel"};
        while (true) {
            int r = JOptionPane.showOptionDialog(parent, picker, "Load Game", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (r != 0) return null;
            Slot s = picker.selected();
            if (s != null) return s;
            JOptionPane.showMessageDialog(parent, "Pick a save to load.", "Load Game", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Text shown for a slot in the picker's first column. */
    static String displayName(Slot s) {
        if (s.name().equals(SaveSlots.UNNAMED_AUTOSAVE)) return "Autosave (unsaved game)";
        if (s.isAutosave()) {
            return s.name().substring(0, s.name().length() - SaveSlots.AUTOSAVE_SUFFIX.length()) + " (autosave)";
        }
        return s.name();
    }

    /** The dialog body: slot table, optional name field, and a Delete button. */
    static final class Picker extends JPanel {
        final SaveSlots slots;
        final boolean saveMode;
        final DefaultTableModel model = new DefaultTableModel(new Object[] {"Save", "Saved at"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        final JTable table = new JTable(model);
        final JTextField nameField = new JTextField(24);
        final JButton delete = new JButton("Delete");
        final JLabel status = new JLabel(" ");
        final List<Slot> rows = new ArrayList<>();

        Picker(SaveSlots slots, boolean saveMode, String initialName) {
            super(new BorderLayout(0, 6));
            this.slots = slots;
            this.saveMode = saveMode;

            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getTableHeader().setReorderingAllowed(false);
            table.getColumnModel().getColumn(1).setPreferredWidth(120);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                Slot s = selected();
                delete.setEnabled(s != null);
                if (saveMode && s != null) nameField.setText(s.name());
            });
            if (!saveMode) {
                // Double-click loads: close the surrounding option pane as if Load was pressed.
                table.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() != 2 || selected() == null) return;
                        JOptionPane pane = (JOptionPane) SwingUtilities.getAncestorOfClass(JOptionPane.class, table);
                        if (pane != null) pane.setValue("Load");
                    }
                });
            }
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(420, 220));
            add(scroll, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout(6, 0));
            if (saveMode) {
                JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                nameRow.add(new JLabel("Save name:"));
                if (initialName != null) nameField.setText(initialName);
                nameRow.add(nameField);
                south.add(nameRow, BorderLayout.CENTER);
            } else {
                south.add(status, BorderLayout.CENTER);
            }
            delete.setEnabled(false);
            delete.addActionListener(e -> deleteSelected());
            south.add(delete, BorderLayout.EAST);
            south.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            add(south, BorderLayout.SOUTH);

            refresh();
        }

        /** Re-reads the saves directory. Save mode hides autosaves: they can be loaded but not saved over. */
        void refresh() {
            rows.clear();
            model.setRowCount(0);
            try {
                for (Slot s : slots.list()) {
                    if (saveMode && s.isAutosave()) continue;
                    rows.add(s);
                    model.addRow(new Object[] {displayName(s), WHEN.format(s.modified())});
                }
                status.setText(rows.isEmpty() ? "No saves yet." : " ");
            } catch (IOException ex) {
                status.setText("Could not read saves: " + ex.getMessage());
            }
            delete.setEnabled(false);
        }

        Slot selected() {
            int i = table.getSelectedRow();
            return i < 0 || i >= rows.size() ? null : rows.get(i);
        }

        private void deleteSelected() {
            Slot s = selected();
            if (s == null) return;
            int ok = JOptionPane.showConfirmDialog(this, "Delete \"" + displayName(s) + "\"? This cannot be undone.",
                "Delete Save", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            try {
                slots.delete(s);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not delete: " + ex.getMessage(),
                    "Delete Save", JOptionPane.ERROR_MESSAGE);
            }
            refresh();
        }
    }
}
